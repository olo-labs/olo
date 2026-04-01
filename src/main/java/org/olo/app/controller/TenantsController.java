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
 * (keys *:olo:kernel:config:*), so dropdown includes UUID tenants and queues show under Chat/RAG.
 * If Redis is unavailable, returns at least the default tenant (no 500).
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Tenant list for UI (default tenant + Redis-discovered)")
public class TenantsController {

    @Value("${olo.default-tenant-id:2a2a91fb-f5b4-4cf0-b917-524d242b2e3d}")
    private String defaultTenantId;

    @Value("${olo.tenant-ids:}")
    private String tenantIds;

    @Autowired(required = false)
    private KernelConfigQueueService queueService;

    private static final String FALLBACK_DEFAULT_TENANT_ID = "2a2a91fb-f5b4-4cf0-b917-524d242b2e3d";

    @Operation(summary = "List tenants", description = "Default tenant (id from olo.default-tenant-id, name Default) first, then Redis-discovered ids. Populates dropdown so queues appear for selected tenant.")
    @GetMapping
    public ResponseEntity<List<TenantDto>> listTenants() {
        try {
            String defaultId = (defaultTenantId != null && !defaultTenantId.isBlank())
                    ? defaultTenantId
                    : FALLBACK_DEFAULT_TENANT_ID;
            Set<String> ids = new LinkedHashSet<>();
            ids.add(defaultId);
            if (tenantIds != null) {
                Arrays.stream(tenantIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> "default".equalsIgnoreCase(s) ? defaultId : s)
                        .forEach(ids::add);
            }
            if (queueService != null) {
                ids.addAll(queueService.getTenantIdsFromRedis());
            }
            List<TenantDto> list = ids.stream()
                    .filter(id -> id != null && !id.isEmpty())
                    .map(id -> new TenantDto(id, id.equals(defaultId) ? "Default" : toDisplayName(id)))
                    .collect(Collectors.toList());
            if (list.isEmpty()) {
                list = List.of(new TenantDto(defaultId, "Default"));
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of(new TenantDto(FALLBACK_DEFAULT_TENANT_ID, "Default")));
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
