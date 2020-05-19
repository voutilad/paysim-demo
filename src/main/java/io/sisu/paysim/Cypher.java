package io.sisu.paysim;

public class Cypher {
  public static final String SENDER_LABEL_PLACEHOLDER = "~STYPE~";
  public static final String RECEIVER_LABEL_PLACEHOLDER = "~RTYPE~";
  public static final String TX_LABEL_PLACEHOLDER = "~XTYPE~";

  public static final String[] SCHEMA_QUERIES = {
    // Core Types
    "CREATE CONSTRAINT ON (c:Client) ASSERT c.id IS UNIQUE",
    "CREATE CONSTRAINT ON (b:Bank) ASSERT b.id IS UNIQUE",
    "CREATE CONSTRAINT ON (m:Merchant) ASSERT m.id IS UNIQUE",
    "CREATE CONSTRAINT ON (m:Mule) ASSERT m.id IS UNIQUE",

    // Transaction Types
    "CREATE CONSTRAINT ON (c:CashIn) ASSERT c.id IS UNIQUE",
    "CREATE CONSTRAINT ON (c:CashOut) ASSERT c.id IS UNIQUE",
    "CREATE CONSTRAINT ON (d:Debit) ASSERT d.id IS UNIQUE",
    "CREATE CONSTRAINT ON (p:Payment) ASSERT p.id IS UNIQUE",
    "CREATE CONSTRAINT ON (t:Transfer) ASSERT t.id IS UNIQUE",
    "CREATE CONSTRAINT ON (tx:Transaction) ASSERT tx.id IS UNIQUE",

    // Identity Types
    "CREATE CONSTRAINT ON (e:Email) ASSERT e.email IS UNIQUE",
    "CREATE CONSTRAINT ON (s:SSN) ASSERT s.ssn IS UNIQUE",
    "CREATE CONSTRAINT ON (p:Phone) ASSERT p.phoneNumber IS UNIQUE",

    // Various Indices
    "CREATE INDEX ON :Transaction(globalStep)",
    "CREATE INDEX ON :CashIn(globalStep)",
    "CREATE INDEX ON :CashOut(globalStep)",
    "CREATE INDEX ON :Debit(globalStep)",
    "CREATE INDEX ON :Payment(globalStep)",
    "CREATE INDEX ON :Transfer(globalStep)",
    "CREATE INDEX ON :Merchant(highRisk)",
    "CREATE INDEX ON :Transaction(fraud)",
  };

  public static final String INSERT_TRANSACTION_QUERY =
      String.join(
          "\n",
          new String[] {
            "MERGE (s:" + SENDER_LABEL_PLACEHOLDER + " { id: $senderId })",
            "MERGE (r:" + RECEIVER_LABEL_PLACEHOLDER + " { id: $receiverId })",
            "CREATE (tx:Transaction:" + TX_LABEL_PLACEHOLDER + " { id: $txId })",
            "SET tx.ts = $ts, tx.amount = $amount, tx.fraud = $fraud,",
            "    tx.step = $step, tx.globalStep = $globalStep",
            "CREATE (s)-[:PERFORMED]->(tx)",
            "CREATE (tx)-[:TO]->(r)",
          });

  public static final String THREAD_TRANSACTIONS_IN_BATCH =
      String.join(
          "\n",
          new String[] {
            "UNWIND $ids AS clientId",
            "MATCH (c:Client {id: clientId})-[:PERFORMED]->(tx:Transaction)",
            "WITH c, tx ORDER BY tx.globalStep",
            "WITH c, collect(tx) AS txs",
            "WITH c, txs, head(txs) AS _start, last(txs) AS _last",
            "MERGE (c)-[:FIRST_TX]->(_start)",
            "MERGE (c)-[:LAST_TX]->(_last)",
            "WITH c, apoc.coll.pairsMin(txs) AS pairs",
            "UNWIND pairs AS pair",
            "WITH pair[0] AS a, pair[1] AS b",
            "MERGE (a)-[n:NEXT]->(b)",
            "RETURN COUNT(n)",
          });

  public static final String CREATE_IDENTITY =
      String.join(
          "\n",
          new String[] {
            "MERGE (c:Client {id: $clientId}) ON MATCH SET c.name = $name",
            "MERGE (s:SSN {ssn: $ssn})",
            "MERGE (e:Email {email: $email})",
            "MERGE (p:Phone {phoneNumber: $phoneNumber})",
            "MERGE (c)-[:HAS_SSN]->(s)",
            "MERGE (c)-[:HAS_EMAIL]->(e)",
            "MERGE (c)-[:HAS_PHONE]->(p)",
          });

  public static final String GET_CLIENT_IDS = "MATCH (c:Client) RETURN c.id";

  public static final String MAKE_MULES_CLIENTS = "MATCH (m:Mule) WHERE NOT m:Client SET m :Client";

  public static final String LABEL_PLACEHOLDER = "~LABEL~";
  public static final String UPDATE_NODE_PROPS =
      "MATCH (n:" + LABEL_PLACEHOLDER + " {id: $id}) SET n += $props";
}
