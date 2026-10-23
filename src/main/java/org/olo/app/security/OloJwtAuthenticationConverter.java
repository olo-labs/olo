/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * Turns a verified JWT into an {@link OloAuthenticationToken}, reading the requested tenant from the tenant header.
 */
public final class OloJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final OloPrincipalResolver resolver;
    private final String tenantHeader;

    public OloJwtAuthenticationConverter(OloPrincipalResolver resolver, String tenantHeader) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.tenantHeader = Objects.requireNonNull(tenantHeader, "tenantHeader");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new OloAuthenticationToken(resolver.resolve(jwt, requestedTenant()), jwt);
    }

    private String requestedTenant() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(tenantHeader);
        }
        return null;
    }
}
