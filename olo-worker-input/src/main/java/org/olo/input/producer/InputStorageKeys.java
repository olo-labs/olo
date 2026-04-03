/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.producer;

import java.util.Objects;

/**
 * Standard key format for cache storage of input values. Both producer and consumer use this so keys are consistent.
 * Multi-tenant: all keys are scoped by tenant id, {@code olo:<tenantId>:worker:<transactionId>:input:<inputName>}.
 * Default tenant id must match {@code olo.default-tenant-id} (e.g. {@code default}).
 */
public final class InputStorageKeys {

    private static final String PREFIX = "olo:worker:";
    /** Default tenant id when none provided; align with backend {@code olo.default-tenant-id}. */
    private static final String DEFAULT_TENANT_ID = "default";

    private InputStorageKeys() {
    }

    /**
     * Builds the cache key for an input value (tenant-scoped).
     * Format: {@code olo:<tenantId>:worker:<transactionId>:input:<inputName>}.
     *
     * @param tenantId      tenant id (same as API / {@code olo.default-tenant-id})
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
