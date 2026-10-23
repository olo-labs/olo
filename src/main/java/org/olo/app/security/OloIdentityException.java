/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;

/**
 * The caller's token was valid, but OLO can't establish an identity for this request: a revoked session, no tenant
 * membership, an ambiguous tenant, or a tenant the user isn't a member of.
 */
public final class OloIdentityException extends AuthenticationException {

    private final HttpStatus status;
    private final String code;

    public OloIdentityException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static OloIdentityException unauthorized(String code, String message) {
        return new OloIdentityException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static OloIdentityException forbidden(String code, String message) {
        return new OloIdentityException(HttpStatus.FORBIDDEN, code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
