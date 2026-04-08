/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.auth.JwtTenantIdDecoder;
import org.olo.configuration.ConfigurationProvider;
import org.olo.configuration.Regions;
import org.olo.configuration.chat.ChatProfiles;
import org.olo.configuration.chat.NamedChatProfile;
import org.olo.configuration.chat.PipelineChatProfilesSection;
import org.olo.configuration.region.TenantRegionResolver;
import org.olo.configuration.snapshot.CompositeConfigurationSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Chat UI context: {@code tenantId} from JWT when present, else {@code olo.default-tenant-id};
 * display name {@code olo.ui.tenant-display-name}; user label {@code olo.ui.user-display-name}.
 */
@RestController
@RequestMapping("/api/ui")
@Tag(name = "UI context", description = "Tenant, display labels, and Olo version for the chat UI")
public class UiContextController {

    private static final String FALLBACK_OLO_VERSION = "v1.0.0-Dev";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /**
     * Chat profile presets from {@code chatProfiles} on a regional pipeline definition (Redis pipelines snapshot).
     * {@code displaySummary} and {@code emoji} come from pipeline JSON ({@code display_summary}, {@code emoji}).
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
            description = "tenantId from JWT when Authorization is sent; otherwise olo.default-tenant-id. "
                    + "tenant = olo.ui.tenant-display-name; user = olo.ui.user-display-name; olo.version. "
                    + "chatProfiles = ordered queue/pipeline presets from standalone Redis "
                    + "{@code olo:config:profiles:&lt;region&gt;} when present, else from the first pipeline entry "
                    + "that defines embedded {@code chatProfiles}, when the configuration snapshot is loaded."
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
        List<ChatProfileDto> profiles = resolveChatProfiles(tid);
        return ResponseEntity.ok(new UiContextDto(tid, tLabel, uLabel, ver, profiles));
    }

    private static List<ChatProfileDto> resolveChatProfiles(String tenantId) {
        try {
            String region = TenantRegionResolver.getRegion(tenantId);
            if (region == null || region.isBlank()) {
                region = Regions.DEFAULT_REGION;
            }
            CompositeConfigurationSnapshot composite = ConfigurationProvider.getComposite(region);
            if (composite == null) {
                return List.of();
            }
            List<ChatProfileDto> fromStandalone = standaloneProfilesToDtos(composite.getProfilesForReuse());
            if (!fromStandalone.isEmpty()) {
                return fromStandalone;
            }
            Map<String, Object> pipelines = composite.getPipelines();
            if (pipelines == null || pipelines.isEmpty()) {
                return List.of();
            }
            for (Object value : pipelines.values()) {
                Map<String, Object> root = pipelineRootAsMap(value);
                if (root == null) {
                    continue;
                }
                Object raw = root.get("chatProfiles");
                if (raw == null) {
                    continue;
                }
                PipelineChatProfilesSection section = MAPPER.convertValue(raw, PipelineChatProfilesSection.class);
                List<NamedChatProfile> named = ChatProfiles.fromPipelineSection(section);
                if (named.isEmpty()) {
                    continue;
                }
                return toChatProfileDtos(named);
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Standalone {@code olo:config:profiles:&lt;region&gt;} document (same root shape as a pipeline {@code chatProfiles} block).
     */
    private static List<ChatProfileDto> standaloneProfilesToDtos(Map<String, Object> profilesRoot) {
        if (profilesRoot == null || profilesRoot.isEmpty()) {
            return List.of();
        }
        Object rawProfiles = profilesRoot.get("profiles");
        if (!(rawProfiles instanceof Map<?, ?> m) || m.isEmpty()) {
            return List.of();
        }
        try {
            PipelineChatProfilesSection section = MAPPER.convertValue(profilesRoot, PipelineChatProfilesSection.class);
            List<NamedChatProfile> named = ChatProfiles.fromPipelineSection(section);
            return toChatProfileDtos(named);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<ChatProfileDto> toChatProfileDtos(List<NamedChatProfile> named) {
        return named.stream()
                .map(n -> new ChatProfileDto(
                        n.id(),
                        n.profile().displayName(),
                        n.profile().displaySummary(),
                        n.profile().emoji(),
                        n.profile().queue(),
                        n.profile().pipeline(),
                        n.profile().runAgain()))
                .toList();
    }

    private static Map<String, Object> pipelineRootAsMap(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) value;
            return m;
        }
        if (value instanceof String s) {
            try {
                return MAPPER.readValue(s, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
