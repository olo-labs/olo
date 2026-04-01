/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of tenant-specific configuration (plugins, features, 3rd party deps, restrictions).
 * Populated at bootstrap from Redis {@link TenantEntry#REDIS_TENANTS_KEY} when entries include a {@code config} object.
 */
public final class TenantConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantConfigRegistry.class);
    private static final TenantConfigRegistry INSTANCE = new TenantConfigRegistry();

    private final Map<String, TenantConfig> byTenant = new ConcurrentHashMap<>();

    public static TenantConfigRegistry getInstance() {
        return INSTANCE;
    }

    private TenantConfigRegistry() {
    }

    /**
     * Registers config for a tenant. Called at bootstrap when parsing olo:tenants.
     *
     * @param tenantId tenant id
     * @param config   tenant config (null or empty = store empty config)
     */
    public void put(String tenantId, TenantConfig config) {
        if (tenantId == null || tenantId.isBlank()) return;
        String tid = tenantId.trim();
        byTenant.put(tid, config != null ? config : TenantConfig.EMPTY);
        log.debug("Registered tenant config for tenant={}", tid);
    }

    /**
     * Registers config for a tenant from a map (e.g. parsed from JSON).
     *
     * @param tenantId   tenant id
     * @param configMap  config key → value (null = empty)
     */
    public void put(String tenantId, Map<String, Object> configMap) {
        put(tenantId, TenantConfig.of(tenantId, configMap));
    }

    /**
     * Returns the tenant config for the given tenant, or {@link TenantConfig#EMPTY} if none registered.
     *
     * @param tenantId tenant id
     * @return tenant config (never null)
     */
    public TenantConfig get(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return TenantConfig.EMPTY;
        return Objects.requireNonNullElse(byTenant.get(tenantId.trim()), TenantConfig.EMPTY);
    }

    /** Clears all registrations (mainly for tests). */
    public void clear() {
        byTenant.clear();
    }
}
