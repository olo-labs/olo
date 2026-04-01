/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import org.springframework.http.server.ServerHttpRequest;

/**
 * Extracts tenantId from Authorization Bearer JWT header.
 * Demo: decodes JWT payload (no signature verification); production must verify.
 */
public interface JwtTenantExtractor {
    String extractTenantId(ServerHttpRequest request);
}
