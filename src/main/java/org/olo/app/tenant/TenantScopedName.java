/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tenant-qualified names for uploaded sources and knowledge sources (M3.11).
 *
 * <p>The API speaks plain names ({@code finance}). Everything stored or sent to workers uses the qualified form
 * {@code <length>-<tenantId>-<name>} ({@code 2-t1-finance}), built from the caller's verified tenant. So:
 * <ul>
 *   <li>a caller can only ever name sources in their own tenant;</li>
 *   <li>upload folders and RAG source tags (the Qdrant {@code capabilitySource} filter) are separate per tenant;</li>
 *   <li>the encoding is injective: the length prefix means no two (tenant, name) pairs give the same key, even when
 *       tenant ids contain {@code -} or {@code _}.</li>
 * </ul>
 * The qualified form is a single path segment that survives the worker's {@code [^a-zA-Z0-9._-] → _} sanitizing,
 * because tenant ids are limited to those characters.
 */
public final class TenantScopedName {

    private static final Pattern TENANT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private TenantScopedName() {
    }

    /** The stored form of {@code name} in {@code tenantId}. */
    public static String qualify(String tenantId, String name) {
        String tenant = requireTenant(tenantId);
        Objects.requireNonNull(name, "name");
        String plain = name.trim();
        if (plain.isEmpty()) {
            throw new IllegalArgumentException("name is required");
        }
        return prefix(tenant) + plain;
    }

    /** The plain name if {@code stored} belongs to {@code tenantId}; empty for another tenant's or unscoped names. */
    public static Optional<String> unqualify(String tenantId, String stored) {
        if (stored == null) {
            return Optional.empty();
        }
        String prefix = prefix(requireTenant(tenantId));
        if (!stored.startsWith(prefix) || stored.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(stored.substring(prefix.length()));
    }

    /** True when {@code stored} is a name in {@code tenantId}. */
    public static boolean belongsTo(String tenantId, String stored) {
        return unqualify(tenantId, stored).isPresent();
    }

    private static String prefix(String tenant) {
        return tenant.length() + "-" + tenant + "-";
    }

    private static String requireTenant(String tenantId) {
        if (tenantId == null || !TENANT_ID.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("invalid tenant id: " + tenantId);
        }
        return tenantId;
    }
}
