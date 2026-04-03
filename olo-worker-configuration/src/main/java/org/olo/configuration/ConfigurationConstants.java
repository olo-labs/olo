/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

/**
 * Shared constants for configuration loading (defaults resource, env prefix).
 * Used by Bootstrap and configuration sources.
 */
public final class ConfigurationConstants {

  /** Default classpath resource name for default values. */
  public static final String DEFAULT_RESOURCE = "olo-defaults.properties";

  /** Env var prefix for overrides (e.g. OLO_DB_HOST → olo.db.host). */
  public static final String ENV_PREFIX = "OLO_";

  private ConfigurationConstants() {}
}
