/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.Regions;
import org.olo.configuration.impl.config.DefaultConfiguration;
import org.olo.configuration.impl.refresh.RedisSnapshotLoader;
import org.olo.configuration.impl.source.DefaultsConfigurationSource;
import org.olo.configuration.impl.source.EnvironmentConfigurationSource;
import org.olo.configuration.port.CacheConnectionSettings;
import org.olo.configuration.port.ConfigurationPortRegistry;
import org.olo.configuration.RedisKeys;
import org.olo.configuration.region.TenantRegionResolver;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.worker.cache.CachePortRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aligns olo backend with olo-worker: registers Redis snapshot factories, loads defaults + env + Spring
 * {@code olo.*} properties, hydrates tenant→region from Redis/DB when ports allow, and deserializes sectioned
 * configuration from Redis using {@link RedisSnapshotLoader} (same types as worker).
 */
@Configuration
public class OloSharedConfigurationInitializer {

    private static final Logger log = LoggerFactory.getLogger(OloSharedConfigurationInitializer.class);

    private static final String[] SPRING_OVERLAY_KEYS = {
            "olo.redis.host", "olo.redis.port", "olo.redis.password", "olo.redis.uri",
            "olo.cache.root-key",
            "olo.region",
            "olo.db.host", "olo.db.port", "olo.db.name", "olo.db.url", "olo.db.username", "olo.db.user", "olo.db.password",
            "olo.config.snapshot.max.redis.value.bytes", "olo.configuration.checksum"
    };

    @Bean
    ApplicationRunner oloLoadSharedConfiguration(Environment springEnv) {
        return (ApplicationArguments args) -> initSharedConfiguration(springEnv);
    }

    static void initSharedConfiguration(Environment springEnv) {
        CachePortRegistrar.registerDefaults();

        Map<String, String> map = new HashMap<>();
        map.putAll(new DefaultsConfigurationSource().load(map));
        map.putAll(new EnvironmentConfigurationSource().load(map));
        overlayFromSpring(springEnv, map);

        String root = map.get("olo.cache.root-key");
        if (root != null && !root.isBlank()) {
            System.setProperty(RedisKeys.ROOT_KEY_PROP, root.trim());
        }

        DefaultConfiguration cfg = new DefaultConfiguration(map);
        ConfigurationProvider.set(cfg);
        ConfigurationProvider.setConfiguredRegions(new ArrayList<>(Regions.getRegions(cfg)));

        TenantRegionResolver.loadFrom(cfg);

        String redisUri = buildRedisUri(map);
        if (redisUri == null || redisUri.isEmpty()) {
            log.info("Olo shared config: no Redis URI; using defaults + env flat configuration only");
            return;
        }

        var factory = ConfigurationPortRegistry.snapshotStoreFactory();
        if (factory == null) {
            log.warn("Olo shared config: snapshot store factory not registered");
            return;
        }

        ConfigurationSnapshotStore store = factory.create(new CacheConnectionSettings(redisUri));
        if (store == null) {
            log.warn("Olo shared config: could not create Redis configuration snapshot store");
            return;
        }

        try {
            List<String> regions = Regions.getRegions(cfg);
            Map<String, CompositeConfigurationSnapshot> snapshotMap = new LinkedHashMap<>();
            String primary = regions.get(0);
            for (String region : regions) {
                CompositeConfigurationSnapshot c = RedisSnapshotLoader.loadComposite(region, store);
                if (c != null) {
                    snapshotMap.put(region, c);
                    log.info("Olo shared config: loaded Redis snapshot for region={} id={}", region, c.getSnapshotId());
                } else {
                    log.debug("Olo shared config: no snapshot in Redis for region={}", region);
                }
            }
            if (!snapshotMap.isEmpty()) {
                ConfigurationProvider.setSnapshotMap(snapshotMap, primary);
            }
        } finally {
            if (store instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    log.debug("Closing Redis snapshot store: {}", e.getMessage());
                }
            }
        }
    }

    private static void overlayFromSpring(Environment env, Map<String, String> map) {
        if (env == null) {
            return;
        }
        for (String key : SPRING_OVERLAY_KEYS) {
            String v = env.getProperty(key);
            if (v != null && !v.isBlank()) {
                map.put(key, v.trim());
            }
        }
    }

    private static String buildRedisUri(Map<String, String> c) {
        String uri = c.getOrDefault("olo.redis.uri", "").trim();
        if (!uri.isEmpty()) {
            return uri;
        }
        String host = c.getOrDefault("olo.redis.host", "").trim();
        if (host.isEmpty()) {
            return "";
        }
        int port = parseInt(c.get("olo.redis.port"), 6379);
        String password = c.getOrDefault("olo.redis.password", "").trim();
        if (password.isEmpty()) {
            return "redis://" + host + ":" + port;
        }
        return "redis://:" + password + "@" + host + ":" + port;
    }

    private static int parseInt(String raw, int def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
