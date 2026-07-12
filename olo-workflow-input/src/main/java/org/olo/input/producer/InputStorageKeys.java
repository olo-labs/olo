/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.producer;

import java.util.Objects;

/**
 * Standard key format for cache storage of input values. Both producer and consumer use this so keys are consistent.
 * Multi-tenant: all keys are scoped by tenant id, {@code olo:<tenantId>:worker:<transactionId>:input:<inputName>}.
 * Default tenant id must match {@link org.olo.config.OloConfig} (OLO_DEFAULT_TENANT_ID / same UUID).
 */
public final class InputStorageKeys {

    private static final String PREFIX = "olo:worker:";
    /** Default tenant id when none provided; must match OloConfig default (UUID). */
    private static final String DEFAULT_TENANT_ID = "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d";

    private InputStorageKeys() {
    }

    /**
     * Builds the cache key for an input value (tenant-scoped).
     * Format: {@code olo:<tenantId>:worker:<transactionId>:input:<inputName>}.
     *
     * @param tenantId      tenant id (use {@link org.olo.config.OloConfig#normalizeTenantId(String)} if from context)
     * @param transactionId the workflow/transaction id
     * @param inputName     the input name
     * @return the cache key
     */
    public static String cacheKey(String tenantId, String transactionId, String inputName) {
        String t = tenantId != null && !tenantId.isBlank() ? tenantId.trim() : DEFAULT_TENANT_ID;
        return "olo:" + t + ":" + PREFIX
                + Objects.requireNonNull(transactionId, "transactionId")
                + ":input:" + Objects.requireNonNull(inputName, "inputName");
    }

    /**
     * Builds the cache key for an input value (legacy; uses default tenant).
     * Prefer {@link #cacheKey(String, String, String)} for multi-tenant.
     */
    public static String cacheKey(String transactionId, String inputName) {
        return cacheKey(DEFAULT_TENANT_ID, transactionId, inputName);
    }
}
