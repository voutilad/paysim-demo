package io.sisu.paysim;

public class Cypher {
  public static final String[] SCHEMA_QUERIES = {
    // Sadness
    "CREATE INDEX IF NOT EXISTS FOR (n:Node) ON (n.id)",

    // Core Types
    "CREATE INDEX IF NOT EXISTS FOR (c:Client) ON (c.id)",
    "CREATE INDEX IF NOT EXISTS FOR (b:Bank) ON (b.id)",
    "CREATE INDEX IF NOT EXISTS FOR (m:Merchant) ON (m.id)",
    "CREATE INDEX IF NOT EXISTS FOR (m:Mule) ON (m.id)",

    // Transaction Types
    "CREATE INDEX IF NOT EXISTS FOR (c:CashIn) ON (c.id)",
    "CREATE INDEX IF NOT EXISTS FOR (c:CashOut) ON (c.id)",
    "CREATE INDEX IF NOT EXISTS FOR (d:Debit) ON (d.id)",
    "CREATE INDEX IF NOT EXISTS FOR (p:Payment) ON (p.id)",
    "CREATE INDEX IF NOT EXISTS FOR (t:Transfer) ON (t.id)",
    "CREATE INDEX IF NOT EXISTS FOR (tx:Transaction) ON (tx.id)",

    // Identity Types
    "CREATE INDEX IF NOT EXISTS FOR (e:Email) ON (e.email)",
    "CREATE INDEX IF NOT EXISTS FOR (s:SSN) ON (s.ssn)",
    "CREATE CONSTINDEXRAINT IF NOT EXISTS FOR (p:Phone) ON (p.phoneNumber)",

    // Various Indices
    "CREATE INDEX IF NOT EXISTS FOR (t:Transaction) ON (t.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (c:CashIn) ON (c.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (c:CashOut) ON (c.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (d:Debit) ON (d.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (p:Payment) ON (p.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (t:Transfer) ON(t.globalStep)",
    "CREATE INDEX IF NOT EXISTS FOR (m:Merchant) ON (m.highRisk)",
    "CREATE INDEX IF NOT EXISTS FOR (t:Transaction) ON (t.fraud)",
  };

  public static final String BULK_NODE_QUERY_STRING =
      String.join(
          "\n",
          new String[] {
            "UNWIND $nodes AS n",
            "  WITH n, coalesce(n.props, {}) AS props",
            "  MERGE (a:Node {id: n.id })",
            "    ON CREATE SET a += props, a.new = true",
            "WITH n, a WHERE a.new",
            "FOREACH(_ IN CASE n.label WHEN 'Client' THEN [1] ELSE [] END | SET a:Client)",
            "FOREACH(_ IN CASE n.label WHEN 'Mule' THEN [1] ELSE [] END | SET a:Client, a:Mule)",
            "FOREACH(_ IN CASE n.label WHEN 'Merchant' THEN [1] ELSE [] END | SET a:Merchant)",
            "FOREACH(_ IN CASE n.label WHEN 'Bank' THEN [1] ELSE [] END | SET a:Bank)",
            "FOREACH(_ IN CASE n.label WHEN 'Payment' THEN [1] ELSE [] END | SET a:Transaction, a:Payment)",
            "FOREACH(_ IN CASE n.label WHEN 'Transfer' THEN [1] ELSE [] END | SET a:Transaction, a:Transfer)",
            "FOREACH(_ IN CASE n.label WHEN 'Debit' THEN [1] ELSE [] END | SET a:Transaction, a:Debit)",
            "FOREACH(_ IN CASE n.label WHEN 'CashIn' THEN [1] ELSE [] END | SET a:Transaction, a:CashIn)",
            "FOREACH(_ IN CASE n.label WHEN 'CashOut' THEN [1] ELSE [] END | SET a:Transaction, a:CashOut)",
            "REMOVE a.new",
            "RETURN count(a)",
          });

  public static final String BULK_TX_PERFORMED_QUERY_STRING =
      String.join(
          "\n",
          new String[] {
            "UNWIND $txs AS tx",
            "  MATCH (s:Node {id: tx.senderId})",
            "  MATCH (t:Node {id: tx.id})",
            "  CREATE (s)-[:PERFORMED]->(t)",
          });

  public static final String BULK_TX_TO_QUERY_STRING =
      String.join(
          "\n",
          new String[] {
            "UNWIND $txs AS tx",
            "  MATCH (r:Node {id: tx.receiverId})",
            "  MATCH (t:Node {id: tx.id})",
            "  CREATE (t)-[:TO]->(r)",
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
            "WITH txs",
            "UNWIND range(1, size(txs) - 1) AS idx",
            "WITH txs[idx-1] AS a, txs[idx] AS b",
            "MERGE (a)-[n:NEXT]->(b)",
            "RETURN COUNT(n)",
          });

  public static final String CREATE_IDENTITY =
      String.join(
          "\n",
          new String[] {
            "MERGE (c:Node {id: $clientId}) ON MATCH SET c.name = $name, c:Client",
            "MERGE (s:SSN {ssn: $ssn})",
            "MERGE (e:Email {email: $email})",
            "MERGE (p:Phone {phoneNumber: $phoneNumber})",
            "MERGE (c)-[:HAS_SSN]->(s)",
            "MERGE (c)-[:HAS_EMAIL]->(e)",
            "MERGE (c)-[:HAS_PHONE]->(p)",
          });

  public static final String GET_CLIENT_IDS = "MATCH (c:Client) RETURN c.id";
  public static final String LABEL_PLACEHOLDER = "~LABEL~";
  public static final String UPDATE_NODE_PROPS =
      "MATCH (n:" + LABEL_PLACEHOLDER + " {id: $id}) SET n += $props";
}
