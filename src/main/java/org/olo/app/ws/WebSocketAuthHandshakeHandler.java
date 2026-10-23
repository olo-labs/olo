/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.ws;

import org.olo.app.security.OloIdentityException;
import org.olo.app.security.OloPrincipal;
import org.olo.app.security.OloPrincipalResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Authenticates the WebSocket handshake with the same verified-JWT rules as the HTTP API, and stores the caller's
 * {@link OloPrincipal} and tenant in the session attributes for the SUBSCRIBE_RUN tenant check.
 *
 * <p>Browsers can't set headers on a WebSocket handshake, so the token may come from the {@code access_token} query
 * parameter, and the tenant from the {@code tenant} parameter. A missing or invalid token is always refused: there is no
 * default tenant.
 */
public class WebSocketAuthHandshakeHandler implements HandshakeHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthHandshakeHandler.class);

    public static final String ATTR_TENANT_ID = "tenantId";
    public static final String ATTR_PRINCIPAL = "oloPrincipal";

    private final HandshakeHandler delegate;
    private final JwtDecoder jwtDecoder;
    private final OloPrincipalResolver resolver;
    private final String tenantHeader;

    public WebSocketAuthHandshakeHandler(HandshakeHandler delegate, JwtDecoder jwtDecoder,
                                         OloPrincipalResolver resolver, String tenantHeader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.tenantHeader = Objects.requireNonNull(tenantHeader, "tenantHeader");
    }

    @Override
    public boolean doHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = bearerToken(request);
        if (token == null) {
            return refuse(response, HttpStatus.UNAUTHORIZED, "missing token");
        }
        OloPrincipal principal;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            principal = resolver.resolve(jwt, requestedTenant(request));
        } catch (OloIdentityException e) {
            return refuse(response, e.status(), e.code());
        } catch (JwtException e) {
            return refuse(response, HttpStatus.UNAUTHORIZED, "invalid token");
        }
        attributes.put(ATTR_PRINCIPAL, principal);
        attributes.put(ATTR_TENANT_ID, principal.tenantId());
        log.debug("[WS] Handshake accepted for user {} in tenant {}", principal.userId(), principal.tenantId());
        return delegate.doHandshake(request, response, wsHandler, attributes);
    }

    private static boolean refuse(ServerHttpResponse response, HttpStatus status, String reason) {
        log.info("[WS] Handshake refused ({}): {}", status.value(), reason);
        response.setStatusCode(status);
        return false;
    }

    private static String bearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ") && !header.substring(7).isBlank()) {
            return header.substring(7).trim();
        }
        String fromQuery = queryParam(request, "access_token");
        return fromQuery == null || fromQuery.isBlank() ? null : fromQuery;
    }

    private String requestedTenant(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(tenantHeader);
        return header != null && !header.isBlank() ? header : queryParam(request, "tenant");
    }

    private static String queryParam(ServerHttpRequest request, String name) {
        if (request.getURI() == null) {
            return null;
        }
        String raw = UriComponentsBuilder.fromUri(request.getURI()).build(true).getQueryParams().getFirst(name);
        return raw == null ? null : URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }
}
