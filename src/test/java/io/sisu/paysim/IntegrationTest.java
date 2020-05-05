package io.sisu.paysim;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.util.List;

public class IntegrationTest {

  @Test
  @Disabled
  void testGettingClientIds() {
    Driver driver = Database.connect("bolt://localhost:7687", "neo4j", "password", false);
    List<String> ids = Database.getClientIds(driver);
    Assertions.assertTrue(ids.size() > 0);
    System.out.println(ids.get(3));
  }
}
