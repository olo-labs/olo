/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.connection;

/**
 * Minimal connection params (from defaults + env only) to connect to Redis/DB for snapshot load.
 */
public final class ConnectionConfig {

  private final String region;
  private final String redisUri;
  private final String jdbcUrl;
  private final String dbUsername;
  private final String dbPassword;
  private final int dbPoolSize;

  public ConnectionConfig(String region, String redisUri, String jdbcUrl, String dbUsername, String dbPassword, int dbPoolSize) {
    this.region = region == null || region.isBlank() ? "default" : region.trim();
    this.redisUri = redisUri == null ? "" : redisUri.trim();
    this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
    this.dbUsername = dbUsername == null ? "" : dbUsername.trim();
    this.dbPassword = dbPassword == null ? "" : dbPassword;
    this.dbPoolSize = dbPoolSize <= 0 ? 5 : dbPoolSize;
  }

  public String getRegion() { return region; }
  public String getRedisUri() { return redisUri; }
  public String getJdbcUrl() { return jdbcUrl; }
  public String getDbUsername() { return dbUsername; }
  public String getDbPassword() { return dbPassword; }
  public int getDbPoolSize() { return dbPoolSize; }

  public boolean hasRedis() { return !redisUri.isEmpty(); }
  public boolean hasDb() { return !jdbcUrl.isEmpty(); }
}
