/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chat UI queue / pipeline catalog from the same Redis section as olo-worker-configuration:
 * {@code <root>:config:pipelines:<region>} (JSON object: pipeline id → pipeline definition).
 * <p>
 * "Queue" in the UI = Temporal task queue / pipeline id (top-level keys). "Pipeline" sub-dropdown
 * uses a {@code pipelines} object inside that definition when present; otherwise a single synthetic row.
 * </p>
 */
@Service
public class KernelConfigQueueService {

    private static final Logger log = LoggerFactory.getLogger(KernelConfigQueueService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${olo.cache.root-key:olo}")
    private String cacheRootKey;

    @Value("${olo.ui.config-region:}")
    private String uiConfigRegion;

    @Value("${olo.region:default}")
    private String configuredRegion;

    public KernelConfigQueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate == null) {
            log.warn("KernelConfigQueueService: Redis not available; queue/pipeline catalog will be empty.");
        }
    }

    /**
     * Sorted pipeline ids (Temporal task queue names) for {@link #pipelinesRedisKey()}.
     */
    public List<String> getQueueNames(String tenantId) {
        Map<String, Object> pipelines = loadPipelinesMap();
        if (pipelines.isEmpty()) {
            return Collections.emptyList();
        }
        return pipelines.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * JSON string for {@link org.olo.app.controller.TenantQueuesController}: shape includes {@code pipelines}
     * for the Pipeline dropdown (same as legacy kernel-config JSON).
     */
    public String getQueueConfig(String tenantId, String queueName) {
        if (queueName == null || queueName.isBlank()) {
            return null;
        }
        Map<String, Object> all = loadPipelinesMap();
        Object def = all.get(queueName);
        if (def == null) {
            return null;
        }
        try {
            if (def instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> asMap = (Map<String, Object>) m;
                if (asMap.containsKey("pipelines")) {
                    return objectMapper.writeValueAsString(asMap);
                }
                return objectMapper.writeValueAsString(wrapSinglePipeline(queueName, asMap));
            }
            return objectMapper.writeValueAsString(wrapSinglePipeline(queueName, Map.of()));
        } catch (Exception e) {
            log.debug("getQueueConfig failed for queueName={}: {}", queueName, e.getMessage());
            return null;
        }
    }

    /**
     * Reserved for tenant listing; sectioned config is per region, not per tenant id. Returns an empty list.
     */
    public List<String> getTenantIdsFromRedis() {
        return List.of();
    }

    private Map<String, Object> loadPipelinesMap() {
        if (redisTemplate == null) {
            return Map.of();
        }
        String key = pipelinesRedisKey();
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                if (log.isDebugEnabled()) {
                    log.debug("No pipelines section at Redis key {}", key);
                }
                return Map.of();
            }
            Map<String, Object> map = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return map != null ? map : Map.of();
        } catch (Exception e) {
            log.warn("Failed to parse pipelines JSON at {}: {}", key, e.getMessage());
            return Map.of();
        }
    }

    /** Redis key: {@code <root>:config:pipelines:<region>}. */
    public String pipelinesRedisKey() {
        String root = (cacheRootKey != null && !cacheRootKey.isBlank()) ? cacheRootKey.trim() : "olo";
        return root + ":config:pipelines:" + resolveRegion();
    }

    private String resolveRegion() {
        if (uiConfigRegion != null && !uiConfigRegion.isBlank()) {
            return uiConfigRegion.trim();
        }
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            return configuredRegion.trim();
        }
        return "default";
    }

    private Map<String, Object> wrapSinglePipeline(String pipelineId, Map<String, Object> definition) {
        Map<String, Object> root = new LinkedHashMap<>();
        Object ver = definition.get("version");
        root.put("version", ver != null ? ver : "1.0");
        Map<String, Object> pipelines = new LinkedHashMap<>();
        String label = displayName(definition, pipelineId);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", label);
        pipelines.put(pipelineId, row);
        root.put("pipelines", pipelines);
        return root;
    }

    private static String displayName(Map<String, Object> definition, String pipelineId) {
        Object n = definition.get("name");
        if (n instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return pipelineId;
    }
}
