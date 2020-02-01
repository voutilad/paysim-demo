package io.sisu.paysim;

import net.sourceforge.argparse4j.inf.Namespace;

import java.util.HashMap;
import java.util.Optional;

public class Config {
    protected static final String DEFAULT_PROPERTIES_FILE = "PaySim.properties";
    protected static final String DEFAULT_USERNAME = "neo4j";
    protected static final String DEFAULT_PASSWORD = "password";
    protected static final String DEFAULT_BOLT_URI = "bolt://localhost:7687";
    protected static final boolean DEFAULT_USE_ENCRYPTION = false;
    protected static final int DEFAULT_BATCH_SIZE = 500;
    protected static final int DEFAULT_SIM_QUEUE_DEPTH = 5000;

    protected static final String KEY_PROPERTIES_FILE = "properties";
    protected static final String KEY_USERNAME = "username";
    protected static final String KEY_PASSWORD = "password";
    protected static final String KEY_BOLT_URI = "uri";
    protected static final String KEY_ENCRYPTION = "tls";
    protected static final String KEY_BATCH_SIZE = "batchSize";
    protected static final String KEY_QUEUE_DEPTH = "queueDepth";

    public final String propertiesFile;
    public final String username;
    public final String password;
    public final String boltUri;
    private final boolean useEncryption;
    public final int batchSize;
    public final int queueDepth;


    Config(Optional<Namespace> configNamespace) {
        Namespace ns = configNamespace.orElse(new Namespace(new HashMap<>()));
        propertiesFile = orString(ns.get(KEY_PROPERTIES_FILE), DEFAULT_PROPERTIES_FILE);
        username = orString(ns.get(KEY_USERNAME), DEFAULT_USERNAME);
        password = orString(ns.get(KEY_PASSWORD), DEFAULT_PASSWORD);
        boltUri = orString(ns.get(KEY_BOLT_URI), DEFAULT_PASSWORD);
        useEncryption = orBool(ns.get(KEY_ENCRYPTION), DEFAULT_USE_ENCRYPTION);
        batchSize = orInt(ns.get(KEY_BATCH_SIZE), DEFAULT_BATCH_SIZE);
        queueDepth = orInt(ns.get(KEY_QUEUE_DEPTH), DEFAULT_SIM_QUEUE_DEPTH);
    }

    private static String orString(Object val, String defaultValue) {
        if (val == null) {
            return defaultValue.toString();
        }
        return defaultValue;
    }

    private static int orInt(Object val, int defaultValue) {
        try {
            return Integer.valueOf(val.toString()).intValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static boolean orBool(Object val, boolean defaultValue) {
        try {
            return Boolean.valueOf(val.toString()).booleanValue();
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
