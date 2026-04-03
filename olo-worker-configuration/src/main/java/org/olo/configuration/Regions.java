/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local configuration for region. Workers must configure exactly one region per process; run additional
 * worker processes to serve additional regions. Tenant→region mapping is stored in DB table
 * {@code olo_configuration_region} and can be cached in Redis.
 */
public final class Regions {

  /** Config key for region (single value; comma-separated lists are rejected at worker bootstrap). */
  public static final String CONFIG_KEY = "olo.region";

  /** Default region when not configured. */
  public static final String DEFAULT_REGION = "default";

  private Regions() {}

  /**
   * Returns the configured region string from {@value #CONFIG_KEY}.
   */
  public static String getRegionList(Configuration config) {
    if (config == null) {
      return DEFAULT_REGION;
    }
    String v = config.get(CONFIG_KEY, "").trim();
    if (v.isEmpty()) {
      return DEFAULT_REGION;
    }
    return v;
  }

  /**
   * Returns the list of regions from config (comma-separated, trimmed, non-empty).
   * If empty or missing, returns a list containing only {@value #DEFAULT_REGION}.
   */
  public static List<String> getRegions(Configuration config) {
    String list = getRegionList(config);
    if (list.isEmpty()) {
      return Collections.singletonList(DEFAULT_REGION);
    }
    List<String> regions = Arrays.stream(list.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
    return regions.isEmpty() ? Collections.singletonList(DEFAULT_REGION) : regions;
  }

  /**
   * Ensures at most one non-empty region is configured. Call from worker bootstrap; multiple regions
   * (e.g. {@code olo.region=default,us-east}) are not supported in a single process.
   *
   * @throws IllegalStateException if more than one region is present after splitting on commas
   */
  public static void enforceSingleRegion(Configuration config) {
    List<String> regions = getRegions(config);
    if (regions.size() > 1) {
      throw new IllegalStateException(
          "Exactly one region is allowed per worker process; configure a single value for "
              + CONFIG_KEY + ". Got: " + getRegionList(config));
    }
  }
}
