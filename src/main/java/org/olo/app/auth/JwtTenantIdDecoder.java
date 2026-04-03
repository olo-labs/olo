/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Decodes {@code tenantId} (then {@code sub}) from a JWT payload without signature verification
 * (same rules as {@link org.olo.app.ws.DefaultJwtTenantExtractor}; demo — production must verify JWT).
 */
@Component
public class JwtTenantIdDecoder {

    private final ObjectMapper objectMapper;

    public JwtTenantIdDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param authorizationHeader value of {@code Authorization} header (e.g. {@code Bearer eyJ...})
     */
    public String tenantIdFromAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        return tenantIdFromJwtToken(token);
    }

    /** Raw JWT string (no {@code Bearer} prefix). */
    public String tenantIdFromJwtToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            if (payload == null) {
                return null;
            }
            Object tenantId = payload.get("tenantId");
            if (tenantId != null && !tenantId.toString().isBlank()) {
                return tenantId.toString().trim();
            }
            Object sub = payload.get("sub");
            if (sub != null && !sub.toString().isBlank()) {
                return sub.toString().trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
