/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.input.producer;

import java.util.Objects;

/**
 * Standard key format for cache storage of input values. Both producer and consumer use this so keys are consistent.
 * Multi-tenant: all keys are scoped by tenant id, {@code olo:<tenantId>:worker:<transactionId>:input:<inputName>}.
 */
public final class InputStorageKeys {

    private static final String PREFIX = "olo:worker:";
    private static final String DEFAULT_TENANT = "default";

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
        String t = tenantId != null && !tenantId.isBlank() ? tenantId.trim() : DEFAULT_TENANT;
        return "olo:" + t + ":" + PREFIX
                + Objects.requireNonNull(transactionId, "transactionId")
                + ":input:" + Objects.requireNonNull(inputName, "inputName");
    }

    /**
     * Builds the cache key for an input value (legacy; uses default tenant).
     * Prefer {@link #cacheKey(String, String, String)} for multi-tenant.
     */
    public static String cacheKey(String transactionId, String inputName) {
        return cacheKey(DEFAULT_TENANT, transactionId, inputName);
    }
}
