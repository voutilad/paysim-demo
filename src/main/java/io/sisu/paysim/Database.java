package io.sisu.paysim;

import org.neo4j.driver.Config;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Database {
  public static final Config encryptedConfig =
      Config.builder().withLogging(Logging.slf4j()).withEncryption().build();
  public static final Config defaultConfig = Config.builder().withLogging(Logging.slf4j()).build();
  private static Logger logger = LoggerFactory.getLogger(Database.class);

  private static class Pair {
      public final long nodes;
      public final long rels;
      public Pair(long nodes, long rels) {
          this.nodes = nodes;
          this.rels = rels;
      }
  }

  public static void enforcePaySimSchema(Driver driver) {
    Arrays.stream(Cypher.SCHEMA_QUERIES)
        .forEach(
            q -> {
              try (Session session = driver.session()) {
                session.run(q);
              } catch (ClientException ce) {
                logger.info("constraint provided by '{}' might already exist", q);
              }
            });

    logger.debug("schema configured");
  }

  public static void execute(Driver driver, Query query) {
    try (Session session = driver.session()) {
      Pair pair = session.writeTransaction(
          tx -> {
            // logger.info(query.toString());
            SummaryCounters summary = tx.run(query).consume().counters();
            return new Pair(summary.nodesCreated(), summary.relationshipsCreated());
          });
        logger.info("created {} nodes, {} relationships", pair.nodes, pair.rels);
    }
  }

  public static int executeBatch(Driver driver, List<Query> queries) {
    try (Session session = driver.session()) {
      final AtomicInteger nodeCnt = new AtomicInteger();
      final AtomicInteger relCnt = new AtomicInteger();
      int cnt =
          session.writeTransaction(
              tx -> {
                queries.forEach(
                    q -> {
                      logger.trace(q.toString());
                      SummaryCounters results = tx.run(q).consume().counters();
                      nodeCnt.addAndGet(results.nodesCreated());
                      relCnt.addAndGet(results.relationshipsCreated());
                    });
                return queries.size();
              });
      logger.info(
          "batch executed {} queries, creating {} nodes and {} relationships",
          cnt,
          nodeCnt.get(),
          relCnt.get());
      return cnt;
    }
  }

  public static List<String> getClientIds(Driver driver) {
    try (Session session = driver.session()) {
      Result result = session.run(Cypher.GET_CLIENT_IDS);
      return result.stream().map(record -> record.get(0).asString()).collect(Collectors.toList());
    }
  }

  public static Driver connect(
      String boltUri, String username, String password, boolean useEncryption) {
    if (useEncryption) {
      return GraphDatabase.driver(boltUri, AuthTokens.basic(username, password), encryptedConfig);
    }
    return GraphDatabase.driver(boltUri, AuthTokens.basic(username, password), defaultConfig);
  }
}
