/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tenant-specific configuration for plugins and features (e.g. 3rd party URLs, API keys, restrictions).
 * Loaded from Redis {@link TenantEntry#REDIS_TENANTS_KEY} when each entry includes a {@code config} object.
 * Passed to {@link org.olo.plugin.ModelExecutorPlugin#execute} and available in
 * {@link org.olo.features.NodeExecutionContext#getTenantConfigMap()} for features.
 */
public interface TenantConfig {

    /** Empty config (no tenant-specific parameters). */
    TenantConfig EMPTY = new TenantConfig() {
        @Override
        public String getTenantId() {
            return "";
        }
        @Override
        public Object get(String key) {
            return null;
        }
        @Override
        public Map<String, Object> getConfigMap() {
            return Collections.emptyMap();
        }
    };

    /** Tenant id this config applies to. */
    String getTenantId();

    /**
     * Gets a config value by key (e.g. "ollamaBaseUrl", "restrictions").
     *
     * @param key config key
     * @return value or null if absent
     */
    Object get(String key);

    /**
     * Returns the full config map (read-only). Use for iteration or passing to features.
     *
     * @return unmodifiable map of config key to value
     */
    Map<String, Object> getConfigMap();

    /**
     * Creates a tenant config from a map (e.g. parsed from Redis).
     *
     * @param tenantId tenant id
     * @param config   config map (may be null; will be copied and made unmodifiable)
     * @return TenantConfig instance
     */
    static TenantConfig of(String tenantId, Map<String, Object> config) {
        if ((tenantId == null || tenantId.isBlank()) && (config == null || config.isEmpty())) {
            return EMPTY;
        }
        String tid = tenantId != null ? tenantId.trim() : "";
        Map<String, Object> copy = config != null && !config.isEmpty()
                ? Collections.unmodifiableMap(new LinkedHashMap<>(config))
                : Collections.emptyMap();
        return new TenantConfig() {
            @Override
            public String getTenantId() {
                return tid;
            }
            @Override
            public Object get(String key) {
                return copy.get(Objects.requireNonNull(key, "key"));
            }
            @Override
            public Map<String, Object> getConfigMap() {
                return copy;
            }
        };
    }
}
