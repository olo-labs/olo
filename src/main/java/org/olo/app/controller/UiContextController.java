/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.olo.app.auth.JwtTenantIdDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat UI context: {@code tenantId} from JWT when present, else {@code olo.default-tenant-id};
 * display name {@code olo.ui.tenant-display-name}; user label {@code olo.ui.user-display-name}.
 */
@RestController
@RequestMapping("/api/ui")
@Tag(name = "UI context", description = "Tenant, display labels, and Olo version for the chat UI")
public class UiContextController {

    private static final String FALLBACK_OLO_VERSION = "v1.0.0-Dev";

    private final JwtTenantIdDecoder jwtTenantIdDecoder;

    @Value("${olo.default-tenant-id:default}")
    private String defaultTenantId;

    @Value("${olo.ui.tenant-display-name:Default}")
    private String tenantDisplayName;

    @Value("${olo.ui.user-display-name:Public}")
    private String userDisplayName;

    @Value("${olo.version:}")
    private String oloVersion;

    public UiContextController(JwtTenantIdDecoder jwtTenantIdDecoder) {
        this.jwtTenantIdDecoder = jwtTenantIdDecoder;
    }

    public record UiContextDto(String tenantId, String tenant, String user, String oloVersion) {}

    @Operation(
            summary = "UI context",
            description = "tenantId from JWT when Authorization is sent; otherwise olo.default-tenant-id. "
                    + "tenant = olo.ui.tenant-display-name; user = olo.ui.user-display-name; olo.version."
    )
    @GetMapping("/context")
    public ResponseEntity<UiContextDto> getContext(HttpServletRequest request) {
        String fromJwt = jwtTenantIdDecoder.tenantIdFromAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION));
        String tid;
        if (fromJwt != null && !fromJwt.isBlank()) {
            tid = fromJwt.trim();
        } else {
            tid = (defaultTenantId != null && !defaultTenantId.isBlank()) ? defaultTenantId.trim() : "default";
        }
        String tLabel = (tenantDisplayName != null && !tenantDisplayName.isBlank())
                ? tenantDisplayName.trim()
                : "Default";
        String uLabel = (userDisplayName != null && !userDisplayName.isBlank())
                ? userDisplayName.trim()
                : "Public";
        String ver = (oloVersion != null && !oloVersion.isBlank())
                ? oloVersion.trim()
                : FALLBACK_OLO_VERSION;
        return ResponseEntity.ok(new UiContextDto(tid, tLabel, uLabel, ver));
    }
}
