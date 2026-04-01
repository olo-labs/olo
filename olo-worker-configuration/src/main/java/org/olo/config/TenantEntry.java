/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tenant entry in the Redis tenant list ({@value #REDIS_TENANTS_KEY}).
 * Used when resolving which tenants to load config for at bootstrap.
 */
public final class TenantEntry {

    /** Redis key for the JSON array of tenants. If missing or invalid, bootstrap uses {@code OLO_TENANT_IDS} from env. */
    public static final String REDIS_TENANTS_KEY = "olo:tenants";

    private final String id;
    private final String name;

    @JsonCreator
    public TenantEntry(@JsonProperty("id") String id, @JsonProperty("name") String name) {
        this.id = id != null ? id.trim() : "";
        this.name = name != null ? name.trim() : "";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Parses a JSON array of tenant objects (e.g. from Redis {@value #REDIS_TENANTS_KEY})
     * and returns the list of tenant ids in order, without duplicates.
     *
     * @param json JSON string array of {@code {"id":"...","name":"..."}}
     * @return list of tenant ids, or empty list if json is null/blank or invalid
     */
    public static List<String> parseTenantIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            Set<String> seen = new LinkedHashSet<>();
            List<String> ids = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.has("id")) continue;
                String id = node.get("id").asText("").trim();
                if (!id.isEmpty() && seen.add(id)) ids.add(id);
            }
            return Collections.unmodifiableList(ids);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Parses a JSON array of tenant objects and returns tenant ids plus config per tenant.
     * Each entry may have {@code "id"}, {@code "name"}, and optional {@code "config"} (object).
     * Use to populate {@link TenantConfigRegistry} at bootstrap.
     *
     * @param json JSON string array of {@code {"id":"...","name":"...","config":{...}}}
     * @return list of entries with id and config map (config may be empty)
     */
    public static List<TenantEntryWithConfig> parseTenantEntriesWithConfig(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isArray()) return List.of();
            List<TenantEntryWithConfig> out = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || !node.has("id")) continue;
                String id = node.get("id").asText("").trim();
                if (id.isEmpty()) continue;
                Map<String, Object> config = new LinkedHashMap<>();
                if (node.has("config") && node.get("config").isObject()) {
                    JsonNode configNode = node.get("config");
                    configNode.fields().forEachRemaining(e ->
                            config.put(e.getKey(), mapper.convertValue(e.getValue(), Object.class)));
                }
                out.add(new TenantEntryWithConfig(id, config));
            }
            return Collections.unmodifiableList(out);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Tenant id plus optional config map (from olo:tenants entry). */
    public static final class TenantEntryWithConfig {
        private final String id;
        private final Map<String, Object> config;

        public TenantEntryWithConfig(String id, Map<String, Object> config) {
            this.id = id;
            this.config = config != null ? Collections.unmodifiableMap(new LinkedHashMap<>(config)) : Map.of();
        }

        public String getId() {
            return id;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }

    /**
     * Serializes a list of tenant ids to the JSON array format expected at {@value #REDIS_TENANTS_KEY}:
     * {@code [{"id":"...","name":""}, ...]}.
     *
     * @param tenantIds list of tenant ids (name is set to empty string)
     * @return JSON string, or "[]" if tenantIds is null or empty
     */
    public static String toJsonArray(List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return "[]";
        }
        List<TenantEntry> entries = tenantIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> new TenantEntry(id.trim(), ""))
                .collect(Collectors.toList());
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantEntry that = (TenantEntry) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
