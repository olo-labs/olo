/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olo.app.security.CurrentPrincipal;
import org.olo.app.security.store.IdentityStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tenant membership and session revocation for OLO administrators (the configured admin role). The path
 * {@code /api/admin/**} is admin-only in {@code SecurityConfig}; each handler checks again.
 *
 * <p>Users are named by their verified {@code (issuer, subject)}, exactly as their tokens will carry them.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Identity admin", description = "Tenant membership and session revocation (OLO admin role)")
public class IdentityAdminController {

    private static final Pattern TENANT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final IdentityStore identityStore;

    public IdentityAdminController(IdentityStore identityStore) {
        this.identityStore = identityStore;
    }

    /** A user named by verified issuer and subject. */
    public record UserRef(String issuer, String subject) {
    }

    /** An IdP session to revoke. */
    public record SessionRef(String issuer, String sessionId) {
    }

    @Operation(summary = "Add tenant member", description = "Makes the user a member of the tenant. Creates the user's stable id if new.")
    @PostMapping("/tenants/{tenantId}/members")
    public ResponseEntity<Map<String, Object>> addMember(@PathVariable String tenantId, @RequestBody UserRef user) {
        CurrentPrincipal.requireAdmin();
        String tenant = validTenant(tenantId);
        String userId = identityStore.userIdFor(required(user.issuer(), "issuer"), required(user.subject(), "subject"));
        identityStore.addMembership(userId, tenant);
        return ResponseEntity.ok(Map.of("userId", userId, "tenantId", tenant));
    }

    @Operation(summary = "Remove tenant member", description = "Removes the membership. Takes effect on the user's next request.")
    @DeleteMapping("/tenants/{tenantId}/members")
    public ResponseEntity<Void> removeMember(@PathVariable String tenantId, @RequestBody UserRef user) {
        CurrentPrincipal.requireAdmin();
        String tenant = validTenant(tenantId);
        identityStore.findUserId(required(user.issuer(), "issuer"), required(user.subject(), "subject"))
                .ifPresent(userId -> identityStore.removeMembership(userId, tenant));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke a session", description = "Revokes one IdP session for everyone holding a token for it.")
    @PostMapping("/auth/sessions/revoke")
    public ResponseEntity<Void> revokeSession(@RequestBody SessionRef session) {
        CurrentPrincipal.requireAdmin();
        identityStore.revokeSession(required(session.issuer(), "issuer"), required(session.sessionId(), "sessionId"));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoke a user's sessions", description = "Revokes every recorded session of the user.")
    @PostMapping("/auth/users/revoke-sessions")
    public ResponseEntity<Map<String, Object>> revokeUserSessions(@RequestBody UserRef user) {
        CurrentPrincipal.requireAdmin();
        int revoked = identityStore.findUserId(required(user.issuer(), "issuer"), required(user.subject(), "subject"))
                .map(identityStore::revokeAllSessions)
                .orElse(0);
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }

    private static String validTenant(String tenantId) {
        if (tenantId == null || !TENANT_ID.matcher(tenantId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid tenant id");
        }
        return tenantId;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " is required");
        }
        return value.trim();
    }
}
