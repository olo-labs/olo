/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import org.olo.app.auth.JwtTenantIdDecoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Reads JWT from Authorization: Bearer header or from query param (accessToken / token) for WebSocket handshake.
 * Decodes payload (Base64), returns tenantId from "tenantId" or "sub" claim.
 * No signature verification (demo only; production must verify JWT).
 */
public class DefaultJwtTenantExtractor implements JwtTenantExtractor {

    private final JwtTenantIdDecoder jwtTenantIdDecoder;

    public DefaultJwtTenantExtractor(JwtTenantIdDecoder jwtTenantIdDecoder) {
        this.jwtTenantIdDecoder = jwtTenantIdDecoder;
    }

    @Override
    public String extractTenantId(ServerHttpRequest request) {
        String token = getTokenFromRequest(request);
        if (token == null || token.isEmpty()) return null;
        return jwtTenantIdDecoder.tenantIdFromJwtToken(token);
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
}
