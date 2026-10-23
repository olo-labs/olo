/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olo.app.security.CurrentPrincipal;
import org.olo.app.security.OloPrincipal;
import org.olo.app.security.store.IdentityStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The caller's own identity and sign-in sessions (M3.2).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Verified identity and session revocation for the caller")
public class AuthController {

    private final IdentityStore identityStore;

    public AuthController(IdentityStore identityStore) {
        this.identityStore = identityStore;
    }

    @Operation(summary = "Who am I", description = "The verified caller: stable user id, tenant, session and its epoch.")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        OloPrincipal principal = CurrentPrincipal.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", principal.userId());
        body.put("tenantId", principal.tenantId());
        body.put("tenants", identityStore.tenantsOf(principal.userId()).stream().sorted().toList());
        body.put("sessionEpoch", principal.sessionEpoch());
        body.put("authExpiresAt", principal.authExpiresAt() != null ? principal.authExpiresAt().toString() : null);
        body.put("admin", principal.admin());
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Sign out", description = "Revokes the current session. Any token for it is refused from now on.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        OloPrincipal principal = CurrentPrincipal.require();
        identityStore.revokeSession(principal.issuer(), principal.sessionId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sign out everywhere", description = "Revokes every recorded session of the caller, including the current one.")
    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Map<String, Object>> revokeAll() {
        OloPrincipal principal = CurrentPrincipal.require();
        int revoked = identityStore.revokeAllSessions(principal.userId());
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }
}
