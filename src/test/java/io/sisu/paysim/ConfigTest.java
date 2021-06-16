package io.sisu.paysim;

import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class ConfigTest {
  @Test
  void naiveConfigTest() {
    Config config = new Config(Optional.empty());
    Assertions.assertNotNull(config);
  }

  @Test
  void canOverrideAnIntegerDefault() {
    Map<String, Object> map = new HashMap<>();
    map.put(Config.KEY_BATCH_SIZE, "6000");
    Namespace ns = new Namespace(map);
    Config config = new Config(Optional.of(ns));
    Assertions.assertEquals(6000, config.batchSize);
  }
}
