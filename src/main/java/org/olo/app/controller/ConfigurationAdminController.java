/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olo.app.config.ConfigurationReloadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local admin endpoints for reloading filesystem configuration without restarting the JVM.
 */
@RestController
@RequestMapping("/api/admin/configuration")
@Tag(name = "Configuration admin", description = "Reload workflow definitions from disk")
public class ConfigurationAdminController {

    private final ConfigurationReloadService reloadService;

    public ConfigurationAdminController(ConfigurationReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @Operation(
            summary = "Reload configuration",
            description = "Rescans olo.configuration.dir and refreshes chat pipeline profiles (/api/ui/context).")
    @PostMapping("/reload")
    public ResponseEntity<ConfigurationReloadService.ConfigurationReloadResult> reload() {
        return ResponseEntity.ok(reloadService.reload());
    }
}
