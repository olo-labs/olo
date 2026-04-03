/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.service.KernelConfigQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Queue and pipeline catalog from the sectioned config snapshot in Redis:
 * {@code <olo.cache.root-key>:config:pipelines:<region>} (same as olo-worker-configuration).
 * Region: {@code olo.ui.config-region}, else {@code olo.region}, else {@code default}.
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenant queues", description = "Pipeline ids (task queues) from Redis olo:config:pipelines:<region>")
public class TenantQueuesController {

    private static final Logger log = LoggerFactory.getLogger(TenantQueuesController.class);

    @Autowired(required = false)
    private KernelConfigQueueService queueService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "List queues", description = "Returns pipeline ids (Temporal task queue names) from the pipelines section for the configured region.")
    @GetMapping("/{tenantId}/queues")
    public ResponseEntity<List<String>> listQueues(@PathVariable String tenantId) {
        List<String> queues = queueService != null ? queueService.getQueueNames(tenantId) : Collections.emptyList();
        if (queues.isEmpty()) {
            log.debug("/api/tenants/{}/queues: no queues (Redis not available or no keys for this tenant)", tenantId);
        }
        return ResponseEntity.ok(queues);
    }

    @Operation(summary = "Get queue config", description = "Returns pipeline definition JSON (with top-level 'pipelines' for the UI) derived from the pipelines section for queueName = pipeline id.")
    @GetMapping("/{tenantId}/queues/{queueName}/config")
    public ResponseEntity<Map<String, Object>> getQueueConfig(
            @PathVariable String tenantId,
            @PathVariable String queueName) {
        if (queueService == null) {
            return ResponseEntity.ok(emptyConfigWithPipelines());
        }
        String raw = queueService.getQueueConfig(tenantId, queueName);
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.ok(emptyConfigWithPipelines());
        }
        try {
            Map<String, Object> config = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            if (config == null) {
                return ResponseEntity.ok(emptyConfigWithPipelines());
            }
            List<Map<String, Object>> pipelines = buildPipelinesForUi(config);
            if (log.isDebugEnabled()) {
                log.debug("Queue config tenantId={} queueName={} topLevelKeys={} pipelinesForUi={}", tenantId, queueName, config.keySet(), pipelines);
            }
            Map<String, Object> response = new LinkedHashMap<>(config);
            response.put("pipelines", pipelines);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.debug("Failed to parse queue config JSON for tenantId={} queueName={}", tenantId, queueName, e);
            return ResponseEntity.ok(emptyConfigWithPipelines());
        }
    }

    private static Map<String, Object> emptyConfigWithPipelines() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pipelines", Collections.emptyList());
        return m;
    }

    /**
     * Builds UI pipeline rows: {@code { "id", "name" }} for each pipeline, matching olo-chat Conversation dropdown.
     * Prefers top-level {@code pipelines} (object map or array); otherwise falls back to recursive id collection.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buildPipelinesForUi(Map<String, Object> config) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object top = config.get("pipelines");
        if (top instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) top).entrySet()) {
                String id = e.getKey();
                if (id == null || id.isBlank()) continue;
                String name = pipelineNameFromEntry(id, e.getValue());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", name.isEmpty() ? id : name);
                out.add(row);
            }
            return out;
        }
        if (top instanceof List) {
            for (Object o : (List<?>) top) {
                if (o instanceof String) {
                    String s = ((String) o).trim();
                    if (s.isEmpty()) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", s);
                    row.put("name", s);
                    out.add(row);
                } else if (o instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) o;
                    Object idObj = m.get("id");
                    Object nameObj = m.get("name");
                    String idStr = idObj != null ? idObj.toString().trim() : "";
                    if (idStr.isEmpty()) continue;
                    String nameStr = nameObj != null ? nameObj.toString().trim() : idStr;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", idStr);
                    row.put("name", nameStr);
                    out.add(row);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<String> flat = new ArrayList<>();
        collectPipelinesFromValue(config, flat);
        for (String s : flat) {
            if (s == null || s.isBlank()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s);
            row.put("name", s);
            out.add(row);
        }
        return out;
    }

    /**
     * Recursively collects pipeline ids (not display names) for fallback when top-level {@code pipelines} is absent.
     */
    @SuppressWarnings("unchecked")
    private static void collectPipelinesFromValue(Object value, List<String> out) {
        if (value == null) return;
        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            Object pipelines = map.get("pipelines");
            if (pipelines instanceof Map) {
                // olo-worker-input format: "pipelines": { "<id>": { "name": "...", ... }, ... }
                for (Map.Entry<String, Object> e : ((Map<String, Object>) pipelines).entrySet()) {
                    String id = e.getKey();
                    if (id == null || id.isBlank()) continue;
                    if (!out.contains(id)) out.add(id);
                }
            } else if (pipelines instanceof List) {
                for (String s : stringListFrom((List<?>) pipelines)) {
                    if (!s.isEmpty() && !out.contains(s)) out.add(s);
                }
            }
            Object pipeline = map.get("pipeline");
            if (pipeline instanceof String) {
                String s = ((String) pipeline).trim();
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
            for (Object v : map.values()) {
                collectPipelinesFromValue(v, out);
            }
            return;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                collectPipelinesFromValue(item, out);
            }
        }
    }

    /** From a pipeline entry value (object with optional "name"), return display name or id. */
    @SuppressWarnings("unchecked")
    private static String pipelineNameFromEntry(String id, Object value) {
        if (value instanceof Map) {
            Object name = ((Map<String, Object>) value).get("name");
            if (name instanceof String) {
                String s = ((String) name).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return id != null ? id.trim() : "";
    }

    private static List<String> stringListFrom(List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String) {
                String s = ((String) o).trim();
                if (!s.isEmpty()) out.add(s);
            } else if (o instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) o;
                Object id = m.get("id");
                Object name = m.get("name");
                Object pipeline = m.get("pipeline");
                String str = id != null ? id.toString() : name != null ? name.toString() : pipeline != null ? pipeline.toString() : null;
                if (str != null && !str.trim().isEmpty()) out.add(str.trim());
            } else if (o != null) {
                out.add(o.toString().trim());
            }
        }
        return out;
    }
}
