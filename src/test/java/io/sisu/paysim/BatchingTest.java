package io.sisu.paysim;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.graphalgo.core.utils.paged.dss.HugeAtomicDisjointSetStruct;
import org.paysim.IteratingPaySim;
import org.paysim.base.Transaction;
import org.paysim.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class BatchingTest {
  static Logger logger = LoggerFactory.getLogger(BatchingTest.class);

  @Test
  public void testBatch() {
    Config config = new Config(Optional.empty());
    IteratingPaySim sim =
        new IteratingPaySim(new Parameters(config.propertiesFile), config.queueDepth);
    sim.run();

    final int batchSize = 5000;
    final List<Transaction> txs = new ArrayList<>(batchSize);

    while (sim.hasNext()) {
      txs.add(sim.next());
      if (txs.size() >= batchSize) {
        doBatch(txs, 12);
        txs.clear();
      }
    }
  }

  public void doBatch(List<Transaction> txs, int parallelism) {
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
            largestCommunity, (txs.size() / parallelism) + (txs.size() % parallelism > 0 ? 1 : 0));
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
    buckets.add(garbage);
    logger.info(
        "bucketed: {} buckets,  {}",
        buckets.stream().filter(bucket -> bucket.size() > 0).count(),
        buckets.stream()
            .filter(bucket -> bucket.size() > 0)
            .map(tx -> tx.size())
            .collect(Collectors.toList())
            .toArray());
    // buckets.stream().filter(bucket -> bucket.size() > 0).forEach(bucket -> logger.info("{}",
    // bucket.size()));
  }

  @Test
  public void math() {
    int x = 9 / 4;
    int y = 9 % 5 > 0 ? 1 : 0;
    logger.info("{} + {} = {}", x, y, x + y);
  }
}
