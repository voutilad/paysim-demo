package io.sisu.paysim;

import com.google.common.collect.Lists;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Values;
import org.paysim.IteratingPaySim;
import org.paysim.PaySimState;
import org.paysim.actors.Client;
import org.paysim.actors.Merchant;
import org.paysim.actors.SuperActor;
import org.paysim.base.Transaction;
import org.paysim.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class App {
  protected static final Logger logger;

  static {
    // Set up nicer logging output.
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "[yyyy-MM-dd'T'HH:mm:ss:SSS]");
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
    logger = LoggerFactory.getLogger(App.class);
  }

  private static ArgumentParser newCsvParser() {
    ArgumentParser parser =
            ArgumentParsers.newFor("paysim-demo csv")
                    .build()
                    .defaultHelp(true)
                    .description("Builds a virtual mobile money network graph in Neo4j");
    parser
            .addArgument("--" + Config.KEY_PROPERTIES_FILE)
            .help("PaySim properties file (with paramFiles adjacent in same dir)")
            .setDefault(Config.DEFAULT_PROPERTIES_FILE);
    parser
            .addArgument("--" + Config.KEY_QUEUE_DEPTH)
            .help("PaySim queue depth")
            .setDefault(Config.DEFAULT_SIM_QUEUE_DEPTH);
    parser
            .addArgument("--" + Config.KEY_OUTPUT_DIR)
            .help("[Csv] Output directory")
            .setDefault(Config.DEFAULT_OUTPUT_DIR);
    return parser;

  }

    private static ArgumentParser newBoltParser() {
    ArgumentParser parser =
        ArgumentParsers.newFor("paysim-demo bolt")
            .build()
            .defaultHelp(true)
            .description("Builds a virtual mobile money network graph in CSV");
    parser
        .addArgument("--" + Config.KEY_PROPERTIES_FILE)
        .help("PaySim properties file (with paramFiles adjacent in same dir)")
        .setDefault(Config.DEFAULT_PROPERTIES_FILE);
    parser
        .addArgument("--" + Config.KEY_BOLT_URI)
        .help("[Bolt] Bolt URI to target Neo4j database")
        .setDefault(Config.DEFAULT_BOLT_URI);
    parser
        .addArgument("--" + Config.KEY_USERNAME)
        .help("[Bolt] neo4j username")
        .setDefault(Config.DEFAULT_USERNAME);
    parser
        .addArgument("--" + Config.KEY_PASSWORD)
        .help("[Bolt] neo4j password")
        .setDefault(Config.DEFAULT_PASSWORD);
    parser
        .addArgument("--" + Config.KEY_ENCRYPTION)
        .help("[Bolt] Use a TLS Bolt connection?")
        .setDefault(Config.DEFAULT_USE_ENCRYPTION);
    parser
        .addArgument("--" + Config.KEY_BATCH_SIZE)
        .help("transaction batch size")
        .setDefault(Config.DEFAULT_BATCH_SIZE);
    parser
        .addArgument("--" + Config.KEY_QUEUE_DEPTH)
        .help("PaySim queue depth")
        .setDefault(Config.DEFAULT_SIM_QUEUE_DEPTH);
    return parser;
  }

  public static void usage() {
    System.err.println("usage: paysim-demo [command] [args]");
    System.err.println("valid commands:");
    System.err.println("\tbolt -- directly populate a remote database");
    System.err.println("\t csv -- dump data out into local csv files");
  }

  public static void main(String[] args) {
    ArgumentParser parser = null;

    if (args.length < 1) {
      usage();
      System.exit(1);
    }

    switch (args[0]) {
      case "bolt":
        try {
          parser = newBoltParser();
          Namespace ns = parser.parseArgs(Arrays.copyOfRange(args, 1, args.length));
          Config config = new Config(Optional.of(ns));
          runBolt(config);
        } catch (ArgumentParserException e) {
          parser.handleError(e);
          System.exit(1);
        } catch (Exception e) {
          logger.error("Failed to run PaySim demo app!", e);
          System.exit(1);
        }
        break;
      case "csv":
        try {
          parser = newCsvParser();
          Namespace ns = parser.parseArgs(Arrays.copyOfRange(args, 1, args.length));
          Config config = new Config(Optional.of(ns));
          runCsv(config);
        } catch (ArgumentParserException e) {
          parser.handleError(e);
          System.exit(1);
        } catch (Exception e) {
          logger.error("Failed to run PaySim demo app!", e);
          System.exit(1);
        }
        break;
      default:
        usage();
        System.exit(1);
    }
  }

  public static void runCsv(Config config) throws IOException {
    Path path = Paths.get(config.outputDirectory).toRealPath();
    if (!path.toFile().exists()) Files.createDirectory(path);

    logger.info("Writing csv output to {}", path);

    IteratingPaySim sim =
        new IteratingPaySim(new Parameters(config.propertiesFile), config.queueDepth);
    sim.run();

    try (OutputStreamWriter writer =
        new OutputStreamWriter(
            new GZIPOutputStream(
                new FileOutputStream(path.resolve("transactions.csv.gz").toFile())))) {
      StatefulBeanToCsv<Transaction> beanToCsv =
          new StatefulBeanToCsvBuilder<Transaction>(writer).withSeparator(',').build();
      logger.info(
          "Simulation started using PaySim v{}, load commencing...please, be patient! :-)",
          PaySimState.PAYSIM_VERSION);
      beanToCsv.write(sim);
    } catch (Exception e) {
      logger.error("crap", e);
      sim.abort();
    }

    logger.info("Wrote transactions.");

    try (FileWriter writer = new FileWriter(path.resolve("clients.csv").toFile())) {
      StatefulBeanToCsv<Client> beanToCsv =
          new StatefulBeanToCsvBuilder<Client>(writer).withSeparator(',').build();
      beanToCsv.write(sim.getClients());
    } catch (Exception e) {
      logger.error("crap", e);
    }

    logger.info("Wrote clients.");

    try (FileWriter writer = new FileWriter(path.resolve("merchants.csv").toFile())) {
      StatefulBeanToCsv<Merchant> beanToCsv =
          new StatefulBeanToCsvBuilder<Merchant>(writer).withSeparator(',').build();
      beanToCsv.write(sim.getMerchants());
    } catch (Exception e) {
      logger.error("crap", e);
    }

    logger.info("Wrote merchants.");
  }

  public static void runBolt(Config config) {
    IteratingPaySim sim =
        new IteratingPaySim(new Parameters(config.propertiesFile), config.queueDepth);

    final List<Transaction> batch = new ArrayList<>(config.batchSize);
    final ZonedDateTime start = ZonedDateTime.now();
    final AtomicInteger atom = new AtomicInteger(0);

    try (Driver driver =
        Database.connect(config.boltUri, config.username, config.password, config.useEncryption)) {
      Database.enforcePaySimSchema(driver);

      try {
        sim.run();
        logger.info(
            "Simulation started using PaySim v{}, load commencing...please, be patient! :-)",
            PaySimState.PAYSIM_VERSION);
        // Batch up Queries based on our Transaction stream for execution
        sim.forEachRemaining(
            t -> {
              batch.add(t);

              if (batch.size() >= config.batchSize) {
                Database.execute(driver, Util.compileNodeTransactionQuery(batch));
                Database.execute(
                    driver,
                    Util.compileBulkTransactionQuery(Cypher.BULK_TX_PERFORMED_QUERY_STRING, batch));
                Database.execute(
                    driver,
                    Util.compileBulkTransactionQuery(Cypher.BULK_TX_TO_QUERY_STRING, batch));
                atom.addAndGet(batch.size());
                batch.clear();
              }
            });

        // Anything left over?
        if (batch.size() > 0) {
          Database.execute(driver, Util.compileNodeTransactionQuery(batch));
          Database.execute(
              driver,
              Util.compileBulkTransactionQuery(Cypher.BULK_TX_PERFORMED_QUERY_STRING, batch));
          Database.execute(
              driver, Util.compileBulkTransactionQuery(Cypher.BULK_TX_TO_QUERY_STRING, batch));
          atom.addAndGet(batch.size());
        }
        logger.info(String.format("[loaded %d PaySim transactions]", atom.get()));
        logger.info(
            String.format(
                "[estimated load rate: %.2f PaySim-transactions/second]",
                (float) atom.get() / Util.toSeconds(Duration.between(start, ZonedDateTime.now()))));

        logger.info("Creating 'identity' materials associated with Client accounts...");
        Lists.partition(sim.getClients(), config.batchSize)
            .forEach(
                chunk -> {
                  List<Query> queries =
                      chunk.stream()
                          .map(
                              client -> Util.compileClientIdentityQuery(client.getClientIdentity()))
                          .collect(Collectors.toList());
                  Database.executeBatch(driver, queries);
                });

        logger.info("Setting any extra node properties for Merchants and Banks...");
        List<SuperActor> allActors =
            Stream.concat(sim.getMerchants().stream(), sim.getBanks().stream())
                .collect(Collectors.toList());
        Lists.partition(allActors, config.batchSize)
            .forEach(
                chunk -> {
                  List<Query> queries =
                      chunk.stream()
                          .map(Util::compilePropertyUpdateQuery)
                          .collect(Collectors.toList());
                  Database.executeBatch(driver, queries);
                });

        logger.info("Threading transactions...");
        final List<String> ids = Database.getClientIds(driver);
        Lists.partition(ids, (config.batchSize / 10))
            .forEach(
                chunk -> {
                  Query query =
                      new Query(
                          Cypher.THREAD_TRANSACTIONS_IN_BATCH, Values.parameters("ids", chunk));
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
    logger.info(
        String.format(
            "Simulation & Load COMPLETED in %dm %ds",
            delta.toMinutes(), Util.toSecondsPart(delta)));
  }
}
