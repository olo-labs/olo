/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.service.KernelConfigQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tenants list for the UI. Merges config (olo.tenant-ids) with tenant ids discovered from Redis
 * (keys *:olo:kernel:config:*). If Redis is unavailable, returns at least the default tenant (no 500).
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Tenant list for UI (default tenant + Redis-discovered)")
public class TenantsController {

    @Value("${olo.default-tenant-id:default}")
    private String defaultTenantId;

    @Value("${olo.ui.tenant-display-name:Default}")
    private String tenantDisplayName;

    @Value("${olo.tenant-ids:}")
    private String tenantIds;

    @Autowired(required = false)
    private KernelConfigQueueService queueService;

    @Operation(summary = "List tenants", description = "Default tenant (olo.default-tenant-id + olo.ui.tenant-display-name) first, then Redis-discovered ids.")
    @GetMapping
    public ResponseEntity<List<TenantDto>> listTenants() {
        try {
            String defaultId = (defaultTenantId != null && !defaultTenantId.isBlank())
                    ? defaultTenantId.trim()
                    : "default";
            String defaultName = (tenantDisplayName != null && !tenantDisplayName.isBlank())
                    ? tenantDisplayName.trim()
                    : "Default";
            Set<String> ids = new LinkedHashSet<>();
            ids.add(defaultId);
            if (tenantIds != null) {
                Arrays.stream(tenantIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(ids::add);
            }
            if (queueService != null) {
                ids.addAll(queueService.getTenantIdsFromRedis());
            }
            final String dn = defaultName;
            final String did = defaultId;
            List<TenantDto> list = ids.stream()
                    .filter(id -> id != null && !id.isEmpty())
                    .map(id -> new TenantDto(id, id.equals(did) ? dn : toDisplayName(id)))
                    .collect(Collectors.toList());
            if (list.isEmpty()) {
                list = List.of(new TenantDto(defaultId, defaultName));
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            String did = (defaultTenantId != null && !defaultTenantId.isBlank()) ? defaultTenantId.trim() : "default";
            String dname = (tenantDisplayName != null && !tenantDisplayName.isBlank()) ? tenantDisplayName.trim() : "Default";
            return ResponseEntity.ok(List.of(new TenantDto(did, dname)));
        }
    }

    private static String toDisplayName(String id) {
        return id.length() > 0 ? id.substring(0, 1).toUpperCase() + (id.length() > 1 ? id.substring(1) : "") : id;
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
