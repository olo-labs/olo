/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.input.model.WorkflowInput;
import org.olo.input.producer.SessionUserInputStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Session cache for the initialize phase: serializes workflow input and pushes it to Redis
 * at the session key ({@code OLO_SESSION_DATA}<transactionId>{@code :USERINPUT}).
 * Serialization excludes null values from JSON.
 * <p>
 * User code should call {@link #cacheUpdate(WorkflowInput)} during workflow start / initialize.
 */
public final class OloSessionCache {

    private static final Logger log = LoggerFactory.getLogger(OloSessionCache.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final OloConfig config;
    private final RedisCacheWriter redisWriter;

    /**
     * Creates a session cache that uses Redis from the given config (OLO_CACHE_HOST, OLO_CACHE_PORT, OLO_SESSION_DATA).
     */
    public OloSessionCache(OloConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.redisWriter = new RedisCacheWriter(config.getCacheHost(), config.getCachePort());
        log.info("OloSessionCache connected to {}:{}", config.getCacheHost(), config.getCachePort());
    }

    /**
     * Increments the tenant's active workflow count in Redis (key {@code <tenantId>:olo:quota:activeWorkflows}).
     * Call when starting a workflow run; pair with {@link #decrActiveWorkflows(String)} in a finally block when the run ends.
     */
    public void incrActiveWorkflows(String tenantId) {
        String key = config.getActiveWorkflowsQuotaKey(tenantId);
        redisWriter.incr(key);
        log.debug("Active workflows +1 for tenant={} (key={})", tenantId, key);
    }

    /**
     * Decrements the tenant's active workflow count in Redis (key {@code <tenantId>:olo:quota:activeWorkflows}).
     * Call when a workflow run ends (success or failure).
     */
    public void decrActiveWorkflows(String tenantId) {
        String key = config.getActiveWorkflowsQuotaKey(tenantId);
        redisWriter.decr(key);
        log.debug("Active workflows -1 for tenant={} (key={})", tenantId, key);
    }

    /**
     * Returns the current active workflow count for the tenant (Redis GET on {@code <tenantId>:olo:quota:activeWorkflows}).
     * Used by quota feature to compare with soft/hard limits. Returns 0 if key is missing or not a number.
     */
    public long getActiveWorkflowsCount(String tenantId) {
        String key = config.getActiveWorkflowsQuotaKey(tenantId);
        return redisWriter.getLong(key);
    }

    /**
     * Serializes the workflow input (excluding null fields from JSON) and pushes it to Redis at the session USERINPUT key.
     * Key is tenant-scoped: &lt;tenantId&gt;:olo:kernel:sessions:&lt;transactionId&gt;:USERINPUT.
     * Call this during the initialize phase.
     *
     * @param input workflow input (version, inputs, context, routing, metadata)
     */
    public void cacheUpdate(WorkflowInput input) {
        Objects.requireNonNull(input, "input");
        String tenantId = OloConfig.normalizeTenantId(
                input.getContext() != null ? input.getContext().getTenantId() : null);
        String transactionId = input.getRouting() != null ? input.getRouting().getTransactionId() : null;
        String key = SessionUserInputStorage.userInputKey(config.getSessionDataPrefix(tenantId), transactionId);
        String value = toJsonExcludingNulls(input);
        redisWriter.put(key, value);
        log.debug("Cache update: session USERINPUT for tenant={} transactionId={}", tenantId, transactionId);
    }

    private static String toJsonExcludingNulls(WorkflowInput input) {
        try {
            return JSON_MAPPER.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize workflow input", e);
        }
    }
}
