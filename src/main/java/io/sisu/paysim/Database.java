package io.sisu.paysim;

import org.neo4j.driver.Config;
import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Database {
    private static Logger logger = LoggerFactory.getLogger(Database.class);

    public static final Config encryptedConfig =
            Config.builder()
                    .withLogging(Logging.slf4j())
                    .withEncryption().build();

    public static final Config defaultConfig =
            Config.builder()
                    .withLogging(Logging.slf4j()).build();

    public static Driver connect(Config config, String username, String password) {
        Driver driver = null;
        try {
            driver = GraphDatabase.driver("bolt://localhost:7687",  AuthTokens.basic(username, password), config);
        } catch (Exception e) {
            logger.error("Failed to initialize bolt connection to Neo4j", e);
        }
        return driver;
    }

    public static void enforcePaySimSchema(Driver driver) {
        try (Session session = driver.session()) {
            Arrays.stream(Cypher.SCHEMA_QUERIES).forEach(q -> session.run(q));
        }
        logger.debug("schema configured");
    }

    public static void execute(Driver driver, Query query) {
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run(query);
                return 1;
            });
        }
    }

    public static int executeBatch(Driver driver, List<Query> queries) {
        try (Session session = driver.session()) {
            int cnt =  session.writeTransaction(tx -> {
                queries.forEach(q -> tx.run(q));
                return queries.size();
            });
            logger.debug(String.format("batch executed %d queries", cnt));
            return cnt;
        }
    }

    public static List<String> getClientIds(Driver driver) {
        try (Session session = driver.session()) {
            Result result = session.run(Cypher.GET_CLIENT_IDS);
            return result.stream()
                    .map(record -> record.get(0).asString())
                    .collect(Collectors.toList());
        }
    }
}
