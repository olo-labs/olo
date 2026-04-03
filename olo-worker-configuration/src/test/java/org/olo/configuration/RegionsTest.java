/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import org.olo.configuration.impl.config.DefaultConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionsTest {

  @Test
  void enforceSingleRegion_acceptsOneRegion() {
    assertDoesNotThrow(() -> Regions.enforceSingleRegion(
        new DefaultConfiguration(Map.of(Regions.CONFIG_KEY, "us-east"))));
  }

  @Test
  void enforceSingleRegion_rejectsMultipleRegions() {
    assertThrows(IllegalStateException.class, () -> Regions.enforceSingleRegion(
        new DefaultConfiguration(Map.of(Regions.CONFIG_KEY, "default,us-east"))));
  }
}
