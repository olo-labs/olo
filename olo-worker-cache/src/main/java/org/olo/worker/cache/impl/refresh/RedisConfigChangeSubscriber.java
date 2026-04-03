/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.worker.cache.impl.refresh;

import org.olo.configuration.RedisKeys;
import org.olo.configuration.port.ConfigChangeSubscriber;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Subscribes to sectioned Redis Pub/Sub channels and invokes callback on change.
 * Channel format: olo:configuration:&lt;section&gt;:&lt;region&gt;.
 */
public final class RedisConfigChangeSubscriber implements ConfigChangeSubscriber {

  private static final Logger log = LoggerFactory.getLogger(RedisConfigChangeSubscriber.class);

  private final RedisClient redisClient;
  private final String channelPrefix;
  private final Set<String> servedRegions;
  private final Runnable onConfigChanged;
  private volatile StatefulRedisPubSubConnection<String, String> pubSubConnection;

  public RedisConfigChangeSubscriber(
      RedisClient redisClient,
      String channelPrefix,
      Set<String> servedRegions,
      Runnable onConfigChanged) {
    this.redisClient = redisClient;
    // Default to configured root config namespace; allow explicit override.
    this.channelPrefix = channelPrefix == null || channelPrefix.isBlank()
        ? RedisKeys.configPrefix()
        : channelPrefix.trim();
    this.servedRegions = normalizeRegions(servedRegions);
    this.onConfigChanged = onConfigChanged;
  }

  @Override
  public void start() {
    if (redisClient == null || onConfigChanged == null) return;
    try {
      StatefulRedisPubSubConnection<String, String> conn = redisClient.connectPubSub();
      conn.addListener(new RedisPubSubAdapter<String, String>() {
        @Override
        public void message(String pattern, String channel, String message) {
          onPatternMessage(channel);
        }
      });
      String[] patterns = subscriptionPatterns();
      conn.sync().psubscribe(patterns);
      pubSubConnection = conn;
      log.info("Config change subscriber started: channelPrefix={} patterns={}", channelPrefix, java.util.Arrays.toString(patterns));
    } catch (Exception e) {
      log.warn("Failed to start config change subscriber for channelPrefix={}: {}", channelPrefix, e.getMessage());
    }
  }

  @Override
  public void stop() {
    StatefulRedisPubSubConnection<String, String> conn = pubSubConnection;
    pubSubConnection = null;
    if (conn != null) {
      try {
        conn.close();
      } catch (Exception e) {
        log.warn("Error closing config change subscriber: {}", e.getMessage());
      }
    }
    if (redisClient != null) {
      try {
        redisClient.shutdown();
      } catch (Exception ignore) {
      }
    }
  }

  private void onPatternMessage(String channel) {
    ChannelEvent event = parseChannel(channel);
    if (event == null) return;
    if (!isServedRegion(event.region)) return;
    try {
      onConfigChanged.run();
      log.info("Triggered config refresh via Pub/Sub: channel={} section={} region={}", channel, event.section, event.region);
    } catch (Exception e) {
      log.warn("Config refresh callback failed for channel={}: {}", channel, e.getMessage());
    }
  }

  private String[] subscriptionPatterns() {
    if (servedRegions.isEmpty()) return new String[] { channelPrefix + ":*:*" };
    String[] patterns = new String[servedRegions.size()];
    int i = 0;
    for (String region : servedRegions) {
      patterns[i++] = channelPrefix + ":*:" + region;
    }
    return patterns;
  }

  private boolean isServedRegion(String region) {
    if (servedRegions.isEmpty()) return true;
    return servedRegions.contains(region.toLowerCase());
  }

  private static Set<String> normalizeRegions(Set<String> regions) {
    if (regions == null || regions.isEmpty()) return Collections.emptySet();
    Set<String> out = new HashSet<>();
    for (String r : regions) {
      if (r != null && !r.isBlank()) out.add(r.trim().toLowerCase());
    }
    return out;
  }

  private ChannelEvent parseChannel(String channel) {
    if (channel == null || channel.isBlank()) return null;
    String expectedPrefix = channelPrefix + ":";
    if (!channel.startsWith(expectedPrefix)) return null;
    String rest = channel.substring(expectedPrefix.length());
    int sep = rest.indexOf(':');
    if (sep <= 0 || sep == rest.length() - 1) return null;
    String section = rest.substring(0, sep).trim();
    String region = rest.substring(sep + 1).trim();
    if (section.isEmpty() || region.isEmpty()) return null;
    return new ChannelEvent(section, region.toLowerCase());
  }

  private static final class ChannelEvent {
    private final String section;
    private final String region;

    private ChannelEvent(String section, String region) {
      this.section = section;
      this.region = region;
    }
  }
}
