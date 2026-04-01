/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Reads JWT from Authorization: Bearer header or from query param (accessToken / token) for WebSocket handshake.
 * Decodes payload (Base64), returns tenantId from "tenantId" or "sub" claim.
 * No signature verification (demo only; production must verify JWT).
 */
public class DefaultJwtTenantExtractor implements JwtTenantExtractor {

    private final ObjectMapper objectMapper;

    public DefaultJwtTenantExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String extractTenantId(ServerHttpRequest request) {
        String token = getTokenFromRequest(request);
        if (token == null || token.isEmpty()) return null;
        return decodeTenantIdFromJwt(token);
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String t = auth.substring(7).trim();
            if (!t.isEmpty()) return t;
        }
        URI uri = request.getURI();
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            int eq = param.indexOf('=');
            if (eq <= 0) continue;
            String key = param.substring(0, eq).trim();
            String value = param.substring(eq + 1).trim();
            if ("accessToken".equals(key) || "token".equals(key)) {
                if (!value.isEmpty()) {
                    try {
                        return URLDecoder.decode(value, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return value;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private String decodeTenantIdFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            if (payload == null) return null;
            Object tenantId = payload.get("tenantId");
            if (tenantId != null && !tenantId.toString().isBlank()) return tenantId.toString();
            Object sub = payload.get("sub");
            if (sub != null && !sub.toString().isBlank()) return sub.toString();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
