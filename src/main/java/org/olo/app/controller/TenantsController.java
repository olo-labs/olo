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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The verified caller's tenants ({@code GET /api/tenants}), from OLO's membership store. Callers see only their own
 * memberships.
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "The caller's tenant memberships")
public class TenantsController {

    private final IdentityStore identityStore;

    @Value("${olo.ui.tenant-display-name:Default}")
    private String tenantDisplayName;

    public TenantsController(IdentityStore identityStore) {
        this.identityStore = identityStore;
    }

    @Operation(summary = "List my tenants", description = "Tenants the verified caller is a member of.")
    @GetMapping
    public ResponseEntity<List<TenantDto>> listTenants() {
        OloPrincipal principal = CurrentPrincipal.require();
        String name = (tenantDisplayName != null && !tenantDisplayName.isBlank()) ? tenantDisplayName.trim() : "Default";
        List<TenantDto> tenants = identityStore.tenantsOf(principal.userId()).stream()
                .sorted()
                .map(id -> new TenantDto(id, id.equals(principal.tenantId()) ? name : id))
                .toList();
        return ResponseEntity.ok(tenants);
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
