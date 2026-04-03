/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.port;

/**
 * Subscription boundary for configuration change events (for example Redis Pub/Sub).
 * Implementations should be non-blocking and invoke the configured callback on event.
 */
public interface ConfigChangeSubscriber {

  void start();

  void stop();
}
