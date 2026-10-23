/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Access to the verified caller inside controllers and services.
 */
public final class CurrentPrincipal {

    private CurrentPrincipal() {
    }

    /** The verified caller. Throws 401 if the request somehow isn't authenticated. */
    public static OloPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OloAuthenticationToken token) {
            return token.getPrincipal();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }

    /**
     * Refuses a tenant id taken from a path or body unless it is the caller's own tenant. A missing value means "my tenant".
     *
     * @return the caller's tenant id
     */
    public static String requireOwnTenant(String tenantFromRequest) {
        OloPrincipal principal = require();
        if (tenantFromRequest != null && !tenantFromRequest.isBlank() && !principal.ownsTenant(tenantFromRequest.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant does not match the caller's tenant");
        }
        return principal.tenantId();
    }

    /** Refuses unless the caller carries the OLO admin role. */
    public static OloPrincipal requireAdmin() {
        OloPrincipal principal = require();
        if (!principal.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OLO admin role required");
        }
        return principal;
    }
}
