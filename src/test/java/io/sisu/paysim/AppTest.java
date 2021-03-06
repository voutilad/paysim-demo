package io.sisu.paysim;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

class AppTest {

  static Logger logger = LoggerFactory.getLogger(AppTest.class);

  @Test
  void testCapitalize() {
    Assertions.assertEquals("CashIn", Util.capitalize("CASH_IN"));
    Assertions.assertEquals("Transfer", Util.capitalize("TRANSFER"));
  }

  @Test
  void testDurationCalc() {
    ZonedDateTime start = ZonedDateTime.now();
    ZonedDateTime stop = ZonedDateTime.now().plus(1245, ChronoUnit.SECONDS);
    Duration delta = Duration.between(start, stop);

    long secondsPart = Util.toSecondsPart(delta);
    Assertions.assertEquals(45, secondsPart);
    logger.info(String.format("completed in %dm %ds", delta.toMinutes(), secondsPart));

    long totalSeconds = Util.toSeconds(delta);
    Assertions.assertEquals(1245, totalSeconds);
    logger.info(
        String.format(
            "estimated load rate: %.2f paysim-transactions/second",
            (float) 415_252 / totalSeconds));
  }
}
