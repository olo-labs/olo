/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

/**
 * Database connection settings used by factory ports.
 */
public record DbConnectionSettings(
    String jdbcUrl,
    String username,
    String password,
    int poolSize) {

  public DbConnectionSettings {
    jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
    username = username == null ? "" : username.trim();
    password = password == null ? "" : password;
    poolSize = poolSize <= 0 ? 5 : poolSize;
  }

  public boolean isConfigured() {
    return !jdbcUrl.isEmpty();
  }
}
