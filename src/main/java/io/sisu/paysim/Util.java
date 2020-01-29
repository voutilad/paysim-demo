package io.sisu.paysim;

import org.neo4j.driver.Query;
import org.neo4j.driver.Values;
import org.paysim.actors.Properties;
import org.paysim.actors.SuperActor;
import org.paysim.base.Transaction;
import org.paysim.identity.ClientIdentity;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Util {

    protected static String capitalize(String string) {
        return Arrays.stream(string.split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                .collect(Collectors.joining(""));
    }

    protected static Map<String, Object> propsFromTx(Transaction t) {
        Map<String, Object> map = new HashMap<>();
        map.put("amount", t.getAmount());
        map.put("fraud", t.isFraud());
        map.put("flaggedFraud", t.isFlaggedFraud());
        map.put("senderId", t.getIdOrig());
        map.put("receiverId", t.getIdDest());
        map.put("senderName", t.getNameOrig());
        map.put("receiverName", t.getNameDest());
        map.put("txId", String.format("tx-%s", t.getGlobalStep()));
        map.put("ts", t.getStep()); // TODO: convert to datetime
        map.put("step", t.getStep());
        map.put("globalStep", t.getGlobalStep());

        return map;
    }

    public static Query compileTransactionQuery(Transaction t) {
        String rawQ = Cypher.INSERT_TRANSACTION_QUERY
                .replace(Cypher.SENDER_LABEL_PLACEHOLDER, capitalize(t.getOrigType().toString()))
                .replace(Cypher.RECEIVER_LABEL_PLACEHOLDER, capitalize(t.getDestType().toString()))
                .replace(Cypher.TX_LABEL_PLACEHOLDER, capitalize(t.getAction()));
        Map<String, Object> props = propsFromTx(t);

        return new Query(rawQ, props);
    }

    public static Query compilePropertyUpdateQuery(SuperActor actor) {
        final String label = capitalize(actor.getType().toString());

        // TODO: right now we're shoving id into the Identity, so to prevent collision remove it...yes this is wasteful
        final Map<String, String> props = actor.getIdentityAsMap();
        props.remove(Properties.ID);

        return new Query(
                Cypher.UPDATE_NODE_PROPS.replace(Cypher.LABEL_PLACEHOLDER, label),
                Values.parameters("id", actor.getId(), "props", props));
    }

    public static Query compileClientIdentityQuery(ClientIdentity identity) {
        return new Query(
                Cypher.CREATE_IDENTITY,
                Values.parameters(
                        "ssn", identity.ssn,
                        "email", identity.email,
                        "name", identity.name,
                        "phoneNumber", identity.phoneNumber,
                        "clientId", identity.id
                )
        );
    }

    /**
     * Helper function to deal with the Java time changes between Java 8 and 11
     * @param duration java.time.Duration
     * @return the equivalent of calling Duration.toSecondsPart()
     */
    public static long toSecondsPart(Duration duration) {
        return toSeconds(duration) % 60;
    }

    /**
     * Helper function to deal with the Java time changes between Java 8 and 11
     * @param duration java.time.Duration
     * @return the equivalent of calling Duration.toSeconds()
     */
    public static long toSeconds(Duration duration) {
        return duration.toMillis() / 1000;
    }
}
