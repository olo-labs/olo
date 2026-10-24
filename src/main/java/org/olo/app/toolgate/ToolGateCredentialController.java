// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import io.swagger.v3.oas.annotations.Operation;
import org.olo.app.security.store.IdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issues a fresh ToolGate user credential for the user a workflow run acts for (M3.4). Only workers reach it, with the
 * signed-callback authentication. The worker holds a non-secret binding reference, never a credential, and the session
 * and tenant membership are checked again on every call, so a revoked session or a removed member gets nothing new.
 */
@RestController
public class ToolGateCredentialController {

    private static final Logger log = LoggerFactory.getLogger(ToolGateCredentialController.class);

    private final ObjectProvider<ToolGateClient> toolGate;
    private final IdentityStore identityStore;

    public ToolGateCredentialController(ObjectProvider<ToolGateClient> toolGate, IdentityStore identityStore) {
        this.toolGate = toolGate;
        this.identityStore = identityStore;
    }

    /** The run's identity binding reference, as the backend put it in the workflow input. */
    public record BindingRef(String tenantId, String userId, String issuer, String sessionId, Long sessionEpoch) {
    }

    @Operation(summary = "ToolGate user credential (worker only)",
            description = "Rechecks the run's session and tenant membership, then exchanges a session assertion at ToolGate.")
    @PostMapping("/api/internal/toolgate/credentials")
    public ResponseEntity<Map<String, Object>> credential(@RequestBody BindingRef ref) {
        ToolGateClient client = toolGate.getIfAvailable();
        if (client == null) {
            return error(HttpStatus.NOT_FOUND, "disabled", "ToolGate user credentials are not enabled");
        }
        if (ref == null || blank(ref.tenantId()) || blank(ref.userId()) || blank(ref.issuer()) || blank(ref.sessionId())
                || ref.sessionEpoch() == null) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "tenantId, userId, issuer, sessionId and sessionEpoch are required");
        }
        var session = identityStore.findSession(ref.issuer(), ref.sessionId()).orElse(null);
        if (session == null || !session.userId().equals(ref.userId()) || session.revoked()
                || session.epoch() != ref.sessionEpoch() || !session.authenticatedUntil().isAfter(java.time.Instant.now())) {
            return error(HttpStatus.FORBIDDEN, "session_ended", "the run's session is no longer valid");
        }
        if (!identityStore.tenantsOf(ref.userId()).contains(ref.tenantId())) {
            return error(HttpStatus.FORBIDDEN, "not_member", "the user is not a member of the run's tenant");
        }
        if (identityStore.pendingRevocations().containsKey(ref.userId())) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "revocation_pending", "user authority is being synchronized");
        }
        try {
            ToolGateClient.Credential credential = client.exchange(ref.tenantId(), ref.userId(), session.authenticatedUntil());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("accessToken", credential.accessToken());
            body.put("tokenType", "Bearer");
            body.put("expiresIn", credential.expiresInSeconds());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        } catch (ToolGateClient.ToolGateException e) {
            log.info("ToolGate refused a user credential for user {}: {} {}", ref.userId(), e.status(), e.error());
            return e.status() == 403
                    ? error(HttpStatus.FORBIDDEN, "user_not_enabled", "ToolGate has not enabled this user")
                    : error(HttpStatus.BAD_GATEWAY, "toolgate_refused", "ToolGate did not issue a credential");
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error", code, "message", message));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
