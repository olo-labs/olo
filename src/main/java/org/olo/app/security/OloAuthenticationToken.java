/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * An authenticated request: the verified JWT plus the {@link OloPrincipal} resolved from it.
 */
public final class OloAuthenticationToken extends AbstractAuthenticationToken {

    public static final String ROLE_ADMIN = "ROLE_OLO_ADMIN";

    private final OloPrincipal principal;
    private final Jwt jwt;

    public OloAuthenticationToken(OloPrincipal principal, Jwt jwt) {
        super(authorities(principal));
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(OloPrincipal principal) {
        return principal.admin() ? List.of(new SimpleGrantedAuthority(ROLE_ADMIN)) : List.of();
    }

    @Override
    public OloPrincipal getPrincipal() {
        return principal;
    }

    /** The raw token is never exposed as credentials, so it can't end up in logs or responses. */
    @Override
    public Object getCredentials() {
        return null;
    }

    public Jwt jwt() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.userId();
    }
}
