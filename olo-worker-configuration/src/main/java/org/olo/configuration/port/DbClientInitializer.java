/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

import java.util.Map;

/**
 * Initializes the DB client from config (e.g. db.url, db.type) and makes it available to the DB module.
 * Called at bootstrap when DB is configured; implementations create the appropriate {@code DbClient}
 * and set it on the provider so repositories can use it.
 */
public interface DbClientInitializer {

  /**
   * Initializes the DB client from the given config map (e.g. db.url, db.username, db.type, db.pool.size).
   * No-op if config is empty or DB not configured.
   */
  void initializeDbClient(Map<String, String> config);
}
