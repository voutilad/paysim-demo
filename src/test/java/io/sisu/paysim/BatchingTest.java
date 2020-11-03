package io.sisu.paysim;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.graphalgo.core.utils.paged.dss.HugeAtomicDisjointSetStruct;
import org.paysim.IteratingPaySim;
import org.paysim.base.Transaction;
import org.paysim.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class BatchingTest {
  static final Logger logger = LoggerFactory.getLogger(BatchingTest.class);
  static Config config = new Config(Optional.empty());
  static Driver driver;

  @BeforeAll
  public static void beforeAll() {
    driver =
        Database.connect("bolt+ssc://35.203.8.95:7687", config.username, config.password, config.useEncryption);
    Database.enforcePaySimSchema(driver);
  }

  @AfterAll
  public static void afterAll() {
    driver.close();
  }

  @Test
  public void testBatch() {
    long total = 0;
    IteratingPaySim sim =
        new IteratingPaySim(new Parameters(config.propertiesFile), config.queueDepth);
    sim.run();

    final int batchSize = 2500;
    final List<Transaction> txs = new ArrayList<>(batchSize);

    final ZonedDateTime start = ZonedDateTime.now();

    while (sim.hasNext()) {
      txs.add(sim.next());
      total++;
      if (txs.size() >= batchSize) {
        final ZonedDateTime batchStart = ZonedDateTime.now();

        List<List<Transaction>> buckets = doBatch(txs, 31);
        batchLoad(buckets);

        final Duration delta = Duration.between(batchStart, ZonedDateTime.now());
        logger.info("batch completed in {}m {}s", delta.toMinutes(), Util.toSecondsPart(delta));

        txs.clear();
      }
    }
    Duration delta = Duration.between(start, ZonedDateTime.now());
    logger.info("FINISHED. Total time: {}m {}s, {} txs", delta.toMinutes(), Util.toSecondsPart(delta), total);
  }

  public void batchLoad(List<List<Transaction>> batches) {
    final List<CompletableFuture<AsyncResult>> tasks = new ArrayList<>();

    logger.info("starting {} tasks", batches.size());
    for (List<Transaction> batch : batches) {
      logger.info("...adding task with {} txs", batch.size());
      tasks.add(Database.executeAsync(driver, Util.compileBulkTransactionQuery(batch)));
    }
    CompletableFuture.allOf(tasks.toArray(new CompletableFuture[tasks.size()])).join();
  }

  public List<List<Transaction>> doBatch(List<Transaction> txs, int parallelism) {
    final Map<String, Long> encoding = new HashMap<>();

    // Collect list of nodes and OneHot encode them
    final AtomicLong idx = new AtomicLong(-1L);
    for (final Transaction tx : txs) {
      encoding.computeIfAbsent(tx.getIdDest(), k -> idx.incrementAndGet());
      encoding.computeIfAbsent(tx.getIdOrig(), k -> idx.incrementAndGet());
    }

    // Populate the DisjointSet
    DisjointSetStruct struct =
        new HugeAtomicDisjointSetStruct(encoding.keySet().size(), AllocationTracker.EMPTY, 4);
    for (Transaction tx : txs) {
      long src = encoding.get(tx.getIdOrig());
      long dest = encoding.get(tx.getIdDest());
      struct.union(src, dest);
    }

    // Build clusterId->size mapping
    final Map<Long, Integer> clusters = new HashMap<>();
    for (String key : encoding.keySet()) {
      final long community = struct.setIdOf(encoding.get(key));
      clusters.put(community, clusters.getOrDefault(community, 0) + 1);
    }
    final int largestCommunity = clusters.values().stream().max(Comparator.naturalOrder()).get();
    final int threshold =
        Math.min(
            largestCommunity, (txs.size() / Math.max(1, parallelism)) + (txs.size() % parallelism > 0 ? 1 : 0));
    logger.info("largest community {}, using threshold {}", largestCommunity, threshold);

    List<List<Transaction>> buckets = new ArrayList<>();
    List<Transaction> garbage = new ArrayList<>();
    List<Integer> estimatedSizes = new ArrayList<>();

    Map<Long, Integer> bucketMappings = new HashMap<>();

    for (int i = 0; i < parallelism; i++) {
      buckets.add(new ArrayList<>());
      estimatedSizes.add(0);
    }
    for (Transaction tx : txs) {
      long community = struct.setIdOf(encoding.get(tx.getIdOrig()));
      int communitySize = clusters.get(community);

      // is community already assigned? if so, add to that bucket
      if (bucketMappings.containsKey(community)) {
        buckets.get(bucketMappings.get(community)).add(tx);
      } else {
        // round-robin through buckets looking for one with space
        boolean assigned = false;
        for (int i = 0; i < buckets.size(); i++) {
          final List<Transaction> bucket = buckets.get(i);
          // bucket doesn't yet have any of this community, so we can add these together
          if (estimatedSizes.get(i) + communitySize <= threshold) {
            // assign community to a bucket index
            // logger.info("assigning community {} (size = {}) to bucket {} (current size = {},
            // threshold = {})",
            //        community, communitySize, i, estimatedSizes.get(i), threshold);
            estimatedSizes.set(i, estimatedSizes.get(i) + communitySize);
            bucketMappings.put(community, i);
            bucket.add(tx);
            assigned = true;
            break;
          }
        }

        if (!assigned) {
          // logger.warn("huh, couldn't assign tx from community {} to bucket", community);
          // toss in garbage
          garbage.add(tx);
        }
      }
    }
    // hack for now...anything that had a problem can just go in another bucket
    buckets.add(garbage);

    // Ditch empties
    buckets.removeIf(bucket -> bucket.size() < 1);

    logger.info(
        "bucketed: {} buckets,  {}",
        buckets.stream().filter(bucket -> bucket.size() > 0).count(),
        buckets.stream()
            .map(tx -> tx.size())
            .collect(Collectors.toList())
            .toArray());
    // buckets.stream().filter(bucket -> bucket.size() > 0).forEach(bucket -> logger.info("{}",
    // bucket.size()));
    return buckets;
  }

  @Test
  public void math() {
    int x = 9 / 4;
    int y = 9 % 5 > 0 ? 1 : 0;
    logger.info("{} + {} = {}", x, y, x + y);
  }
}
