/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.olo.app.auth.JwtTenantIdDecoder;
import org.olo.app.config.RegionalConfigurationRegistry;
import org.olo.app.config.RegionalConfigurationSnapshot;
import org.olo.definition.workflow.WorkflowDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat UI context: {@code tenantId} from JWT when present, else tenant from {@code olo.configuration.dir};
 * display name {@code olo.ui.tenant-display-name}; user label {@code olo.ui.user-display-name}.
 */
@RestController
@RequestMapping("/api/ui")
@Tag(name = "UI context", description = "Tenant, display labels, and Olo version for the chat UI")
public class UiContextController {

    private static final String FALLBACK_OLO_VERSION = "v1.0.0-Dev";

    private final JwtTenantIdDecoder jwtTenantIdDecoder;
    private final RegionalConfigurationRegistry configurationRegistry;

    private final String defaultTenantId;

    @Value("${olo.ui.tenant-display-name:Default}")
    private String tenantDisplayName;

    @Value("${olo.ui.user-display-name:Public}")
    private String userDisplayName;

    @Value("${olo.version:}")
    private String oloVersion;

    public UiContextController(JwtTenantIdDecoder jwtTenantIdDecoder,
                               @Qualifier("oloDefaultTenantId") String defaultTenantId,
                               @Autowired(required = false) RegionalConfigurationRegistry configurationRegistry) {
        this.jwtTenantIdDecoder = jwtTenantIdDecoder;
        this.defaultTenantId = defaultTenantId;
        this.configurationRegistry = configurationRegistry;
    }

    /**
     * Chat profile preset derived from a regional {@link WorkflowDefinition}.
     */
    public record ChatProfileDto(
            String id,
            String displayName,
            String displaySummary,
            String emoji,
            String queue,
            String pipeline,
            boolean runAgain) {}

    public record UiContextDto(
            String tenantId,
            String tenant,
            String user,
            String oloVersion,
            List<ChatProfileDto> chatProfiles) {

        public UiContextDto {
            chatProfiles = chatProfiles == null ? List.of() : List.copyOf(chatProfiles);
        }
    }

    @Operation(
            summary = "UI context",
            description = "tenantId from JWT when Authorization is sent; otherwise tenant from olo.configuration.dir (default region folder). "
                    + "tenant = olo.ui.tenant-display-name; user = olo.ui.user-display-name; olo.version. "
                    + "chatProfiles = workflow definitions under olo.configuration.dir/<region>/ (*.json): "
                    + "role=displayName, shortDescription=displaySummary, emoji, queue, id=pipeline."
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
        List<ChatProfileDto> profiles = resolveChatProfiles();
        return ResponseEntity.ok(new UiContextDto(tid, tLabel, uLabel, ver, profiles));
    }

    private List<ChatProfileDto> resolveChatProfiles() {
        try {
            if (configurationRegistry == null || !configurationRegistry.isLoaded()) {
                return List.of();
            }
            RegionalConfigurationSnapshot snap = configurationRegistry.get(configurationRegistry.getDefaultRegion());
            if (snap == null || snap.getWorkflows().isEmpty()) {
                return List.of();
            }
            List<ChatProfileDto> out = new ArrayList<>();
            for (WorkflowDefinition workflow : snap.getWorkflows()) {
                out.add(toChatProfile(workflow));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static ChatProfileDto toChatProfile(WorkflowDefinition workflow) {
        String id = workflow.getId();
        String displayName = nonBlank(workflow.getRole(), workflow.getName(), id);
        String displaySummary = workflow.getShortDescription() != null ? workflow.getShortDescription() : "";
        String emoji = workflow.getEmoji() != null ? workflow.getEmoji() : "";
        String queue = workflow.getQueue() != null ? workflow.getQueue() : "";
        boolean runAgain = Boolean.TRUE.equals(workflow.isRunAgain());
        return new ChatProfileDto(id, displayName, displaySummary, emoji, queue, id, runAgain);
    }

    private static String nonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return "";
    }

}
