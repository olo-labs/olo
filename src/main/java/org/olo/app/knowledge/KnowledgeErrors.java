// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.knowledge;

import org.olo.toolgate.client.GovernedCalls;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice(assignableTypes = KnowledgeController.class)
public final class KnowledgeErrors {
    @ExceptionHandler(GovernedCalls.Pending.class)
    ResponseEntity<?> pending() {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(
                        Map.of(
                                "state",
                                "PENDING_APPROVAL",
                                "resume",
                                "Repeat this request with the same Idempotency-Key"));
    }

    @ExceptionHandler(GovernedCalls.ReconciliationRequired.class)
    ResponseEntity<?> unknown(GovernedCalls.ReconciliationRequired failure) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("state", "OUTCOME_UNKNOWN");
        body.put("message", "Reconcile the storage receipt before another write");
        if (failure.invocationId() != null) body.put("invocationId", failure.invocationId());
        return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<?> denied() {
        return ResponseEntity.status(403)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Access denied"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }

    @ExceptionHandler(org.olo.toolgate.client.ToolGateException.class)
    ResponseEntity<?> gateway(org.olo.toolgate.client.ToolGateException failure) {
        int status =
                switch (failure.kind()) {
                    case UNAUTHORIZED, FORBIDDEN, CREDENTIAL_MISSING -> 403;
                    default -> 503;
                };
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Governed capability unavailable"));
    }
}
