package io.sisu.paysim;

import com.google.common.collect.Lists;
import org.neo4j.driver.*;
import org.paysim.IteratingPaySim;
import org.paysim.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class App {
    protected static final Logger logger = LoggerFactory.getLogger(App.class);

    private static final int BATCH_SIZE = 500;

    public static void main(String[] args) {
        IteratingPaySim sim = new IteratingPaySim(new Parameters("PaySim.properties"), 5000);

        final List<Query> batch = new ArrayList<>(BATCH_SIZE);
        final ZonedDateTime start = ZonedDateTime.now();
        final AtomicInteger atom = new AtomicInteger(0);

        try (Driver driver = Database.connect(Database.defaultConfig, "neo4j", "password")) {
            Database.enforcePaySimSchema(driver);

            try {
                sim.run();
                logger.info("Simulation started, load commencing...please, be patient :-)");
                // Batch up Queries based on our Transaction stream for execution
                sim.forEachRemaining(t -> {
                    batch.add(Util.compileTransactionQuery(t));

                    if (batch.size() >= BATCH_SIZE) {
                        atom.addAndGet(Database.executeBatch(driver, batch));
                        batch.clear();
                    }
                });

                // Anything left over?
                if (batch.size() > 0) {
                    atom.addAndGet(Database.executeBatch(driver, batch));
                }
                logger.info(String.format("loaded %d paysim transactions", atom.get()));
                logger.info(String.format("estimated load rate: %.2f paysim-transactions/second",
                        (float) atom.get() / Util.toSeconds(Duration.between(start, ZonedDateTime.now()))));

                // Update any client properties in batch transactions
                Lists.partition(sim.getClients(), 100).forEach(chunk -> {
                    final String qs = "MERGE (c:Client {id: $id}) ON MATCH SET c += $props";
                    try (final Session session = driver.session()) {
                        chunk.forEach(c -> {
                            session.writeTransaction(tx -> tx.run(qs, Values.parameters("id", c.getId(), "props", c.getProperties())));
                        });
                    }
                });

                // Time to thread transactions into chains...
                logger.info("->-> Threading transactions... ->->");
                driver.session().run(Cypher.MAKE_MULES_CLIENTS);
                final List<String> ids = Database.getClientIds(driver);

                Lists.partition(ids, 100).forEach(chunk -> {
                    Query query = new Query(Cypher.THREAD_TRANSACTIONS_IN_BATCH, Values.parameters("ids", chunk));
                    Database.execute(driver, query);
                });
                logger.info("->-> Threading complete. ->->");

            } catch (Exception e) {
                logger.error("exception while loading data", e);
                try {
                    sim.abort();
                } catch (IllegalStateException ise) {
                    logger.warn("sim already aborted");
                }
            }
        }

        Duration delta = Duration.between(start, ZonedDateTime.now());
        logger.info(String.format("completed in %dm %ds", delta.toMinutes(), Util.toSecondsPart(delta)));
    }
}
