package io.sisu.paysim;

import org.neo4j.driver.Query;
import org.neo4j.driver.summary.ResultSummary;

import java.util.concurrent.TimeUnit;

public class AsyncResult {
  public final Query query;
  public final long nodesCreated;
  public final long relsCreated;
  public final long availableAfterMs;
  public final long consumedAfterMs;

  public AsyncResult(Query query, ResultSummary summary) {
    this.query = query;
    nodesCreated = summary.counters().nodesCreated();
    relsCreated = summary.counters().relationshipsCreated();
    availableAfterMs = summary.resultAvailableAfter(TimeUnit.MILLISECONDS);
    consumedAfterMs = summary.resultConsumedAfter(TimeUnit.MILLISECONDS);
  }

  @Override
  public String toString() {
    return String.format(
        "{ nodesCreated: %d, relsCreated: %d, availableAfterMs: %d, consumedAfterMs: %d",
        nodesCreated, relsCreated, availableAfterMs, consumedAfterMs);
  }
}
