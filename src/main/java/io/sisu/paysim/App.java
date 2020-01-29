package io.sisu.paysim;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Values;
import org.paysim.IteratingPaySim;
import org.paysim.actors.SuperActor;
import org.paysim.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
                logger.info("Simulation started, load commencing...please, be patient! :-)");
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
                logger.info(String.format("[loaded %d PaySim transactions]", atom.get()));
                logger.info(String.format("[estimated load rate: %.2f PaySim-transactions/second]",
                        (float) atom.get() / Util.toSeconds(Duration.between(start, ZonedDateTime.now()))));

                logger.info("Labeling all Mules as Clients...");
                driver.session().run(Cypher.MAKE_MULES_CLIENTS);

                logger.info("Creating 'identity' materials associated with Client accounts...");
                Lists.partition(sim.getClients(), BATCH_SIZE)
                        .forEach(chunk -> {
                            List<Query> queries = chunk.stream()
                                    .map(client -> Util.compileClientIdentityQuery(client.getClientIdentity()))
                                    .collect(Collectors.toList());
                            Database.executeBatch(driver, queries);
                        });

                logger.info("Setting any extra node properties for Merchants and Banks...");
                List<SuperActor> allActors = Streams.concat(
                        sim.getMerchants().stream(),
                        sim.getBanks().stream()).collect(Collectors.toList());
                Lists.partition(allActors, BATCH_SIZE)
                        .forEach(chunk -> {
                            List<Query> queries = chunk.stream()
                                    .map(actor -> Util.compilePropertyUpdateQuery(actor))
                                    .collect(Collectors.toList());
                            Database.executeBatch(driver, queries);
                        });

                logger.info("Threading transactions...");
                final List<String> ids = Database.getClientIds(driver);
                Lists.partition(ids, BATCH_SIZE).forEach(chunk -> {
                    Query query = new Query(Cypher.THREAD_TRANSACTIONS_IN_BATCH, Values.parameters("ids", chunk));
                    Database.execute(driver, query);
                });

            } catch (Exception e) {
                logger.error("EXCEPTION while loading data", e);
                try {
                    sim.abort();
                } catch (IllegalStateException ise) {
                    logger.warn("sim already aborted!");
                }
            }
        }

        Duration delta = Duration.between(start, ZonedDateTime.now());
        logger.info(String.format("Simulation & Load COMPLETED in %dm %ds", delta.toMinutes(), Util.toSecondsPart(delta)));
    }
}
