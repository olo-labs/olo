/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import org.olo.configuration.impl.config.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultConfigurationTest {

  @Test
  void getAndTypedAccess() {
    Configuration c = new DefaultConfiguration(Map.of(
        "k", "v",
        "num", "42",
        "flag", "true"
    ));
    assertEquals("v", c.get("k"));
    assertEquals("default", c.get("missing", "default"));
    assertEquals(42, c.getInteger("num", 0));
    assertEquals(0, c.getInteger("missing", 0));
    assertTrue(c.getBoolean("flag"));
    assertFalse(c.getBoolean("missing"));
    assertTrue(c.getBoolean("missing", true));
  }
}
