package io.sisu.paysim;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class App {
    protected static final Logger logger = LoggerFactory.getLogger(App.class);

    private static ArgumentParser newParser() {
        ArgumentParser parser = ArgumentParsers.newFor("paysim-demo")
                .build()
                .defaultHelp(true)
                .description("Builds a virtual mobile money network graph in Neo4j");
        parser.addArgument("--" + Config.KEY_PROPERTIES_FILE)
                .help("PaySim properties file (with paramFiles adjacent in same dir)")
                .setDefault(Config.DEFAULT_PROPERTIES_FILE);
        parser.addArgument("--" + Config.KEY_BOLT_URI)
                .help("Bolt URI to target Neo4j database")
                .setDefault(Config.DEFAULT_BOLT_URI);
        parser.addArgument("--" + Config.KEY_USERNAME).setDefault(Config.DEFAULT_USERNAME);
        parser.addArgument("--" + Config.KEY_PASSWORD).setDefault(Config.DEFAULT_PASSWORD);
        parser.addArgument("--" + Config.KEY_ENCRYPTION)
                .help("Ues a TLS Bolt connection?")
                .setDefault(Config.DEFAULT_USE_ENCRYPTION);
        parser.addArgument("--" + Config.KEY_BATCH_SIZE)
                .help("transaction batch size")
                .setDefault(Config.DEFAULT_BATCH_SIZE);
        parser.addArgument("--" + Config.KEY_QUEUE_DEPTH)
                .help("PaySim queue depth").
                setDefault(Config.DEFAULT_SIM_QUEUE_DEPTH);
        return parser;
    }

    public static void main(String[] args) {
        ArgumentParser parser = newParser();
        try {
            Namespace ns = parser.parseArgs(args);
            Config config = new Config(Optional.of(ns));
            run(config);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        } catch (Exception e) {
            logger.error("unhandled exception! (this is a bug)", e);
        }
    }

    public static void run(Config config) {
        IteratingPaySim sim = new IteratingPaySim(new Parameters(config.propertiesFile), config.queueDepth);

        final List<Query> batch = new ArrayList<>(config.batchSize);
        final ZonedDateTime start = ZonedDateTime.now();
        final AtomicInteger atom = new AtomicInteger(0);

        try (Driver driver = Database.connect(Database.defaultConfig, config.username, config.password)) {
            Database.enforcePaySimSchema(driver);

            try {
                sim.run();
                logger.info("Simulation started, load commencing...please, be patient! :-)");
                // Batch up Queries based on our Transaction stream for execution
                sim.forEachRemaining(t -> {
                    batch.add(Util.compileTransactionQuery(t));

                    if (batch.size() >= config.batchSize) {
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
                Lists.partition(sim.getClients(), config.batchSize)
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
                Lists.partition(allActors, config.batchSize)
                        .forEach(chunk -> {
                            List<Query> queries = chunk.stream()
                                    .map(actor -> Util.compilePropertyUpdateQuery(actor))
                                    .collect(Collectors.toList());
                            Database.executeBatch(driver, queries);
                        });

                logger.info("Threading transactions...");
                final List<String> ids = Database.getClientIds(driver);
                Lists.partition(ids, config.batchSize).forEach(chunk -> {
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
