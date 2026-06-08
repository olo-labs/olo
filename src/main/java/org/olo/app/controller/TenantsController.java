/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant list for the UI ({@code GET /api/tenants}) from {@code olo.configuration.dir}.
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Tenant list for UI")
public class TenantsController {

    private final String defaultTenantId;

    @Value("${olo.ui.tenant-display-name:Default}")
    private String tenantDisplayName;

    public TenantsController(@Qualifier("oloDefaultTenantId") String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    @Operation(summary = "List tenants", description = "Returns the tenant from olo.configuration.dir (default region folder).")
    @GetMapping
    public ResponseEntity<List<TenantDto>> listTenants() {
        String id = (defaultTenantId != null && !defaultTenantId.isBlank()) ? defaultTenantId.trim() : "default";
        String name = (tenantDisplayName != null && !tenantDisplayName.isBlank()) ? tenantDisplayName.trim() : "Default";
        return ResponseEntity.ok(List.of(new TenantDto(id, name)));
    }

    public static final class TenantDto {
        private final String id;
        private final String name;

        public TenantDto(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }
}
