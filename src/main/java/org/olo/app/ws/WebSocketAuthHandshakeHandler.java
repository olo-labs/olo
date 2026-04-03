/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;

import java.util.Map;

/**
 * Validates Authorization Bearer JWT during WebSocket handshake, extracts tenantId,
 * and stores it in session attributes for SUBSCRIBE_RUN tenant check.
 * When JWT is not required (e.g. for Swagger/local testing), missing/invalid JWT
 * uses a default tenant instead of rejecting with 401.
 */
public class WebSocketAuthHandshakeHandler implements HandshakeHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeHandler.class);

    public static final String ATTR_TENANT_ID = "tenantId";

    private final HandshakeHandler delegate;
    private final JwtTenantExtractor jwtTenantExtractor;
    private final boolean jwtRequired;
    private final String defaultTenantId;

    public WebSocketAuthHandshakeHandler(HandshakeHandler delegate, JwtTenantExtractor jwtTenantExtractor,
                                         boolean jwtRequired, String defaultTenantId) {
        this.delegate = delegate;
        this.jwtTenantExtractor = jwtTenantExtractor;
        this.jwtRequired = jwtRequired;
        this.defaultTenantId = defaultTenantId != null && !defaultTenantId.isBlank() ? defaultTenantId.trim() : "default";
    }

    @Override
    public boolean doHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String tenantId = jwtTenantExtractor.extractTenantId(request);
        if (tenantId == null || tenantId.isBlank()) {
            if (jwtRequired) {
                log.info("[WS] Handshake rejected: JWT required but missing or invalid tenant");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            tenantId = this.defaultTenantId;
            log.debug("[WS] Handshake: no token, using default tenant {}", tenantId);
        } else {
            log.debug("[WS] Handshake: tenant from token {}", tenantId);
        }
        attributes.put(ATTR_TENANT_ID, tenantId);
        return delegate.doHandshake(request, response, wsHandler, attributes);
    }
}
