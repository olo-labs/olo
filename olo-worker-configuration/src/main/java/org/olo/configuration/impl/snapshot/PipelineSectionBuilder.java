/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.olo.configuration.snapshot.PipelineRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds Redis snapshot sections from DB: {@code olo_pipeline_template.tree_json} (pipeline logic),
 * {@code queues_json} and {@code profiles_json} (execution + UI binding layers).
 * <p>
 * Pipelines are written to Redis as {@code { "pipelines": { pipelineId → tree } }}; queues and profiles match
 * {@code olo:config:queues:&lt;region&gt;} and {@code olo:config:profiles:&lt;region&gt;} (documents with
 * {@code "queues"} / {@code "profiles"} roots). Optional overrides {@code olo_config_section} are applied first.
 * </p>
 */
public final class PipelineSectionBuilder {

  private static final Logger log = LoggerFactory.getLogger(PipelineSectionBuilder.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static final String SECTION_QUEUES = "queues";
  static final String SECTION_PROFILES = "profiles";

  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final ConfigurationSnapshotStore store;

  public PipelineSectionBuilder(String jdbcUrl, String username, String password, ConfigurationSnapshotStore store) {
    this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password.trim();
    this.store = store;
  }

  /**
   * Loads active pipelines for region from DB and stores them in Redis (wrapped {@code pipelines} envelope).
   */
  public Map<String, Object> buildAndStore(String region) {
    if (store == null) return null;
    String r = normalizeRegion(region);
    if (jdbcUrl.isEmpty()) return null;

    Map<String, Object> innerPipelines = loadInnerPipelinesFromDb(r);
    if (innerPipelines == null) return null;
    try {
      Map<String, Object> redisPipelines = new LinkedHashMap<>();
      redisPipelines.put("pipelines", innerPipelines);
      store.putPipelines(r, redisPipelines);
      log.info("Built and stored pipelines section for region={} pipelines={}", r, innerPipelines.size());
      ensureQueuesAndProfiles(r);
      return redisPipelines;
    } catch (Exception e) {
      log.warn("Failed to store pipelines section to Redis for region={}: {}", r, e.getMessage());
      return null;
    }
  }

  /**
   * Read-through: if Redis lacks queues or profiles keys, populate from {@code olo_config_section}, then
   * {@code olo_pipeline_template.queues_json / profiles_json}, then legacy derivation from embedded {@code chatProfiles}.
   */
  public void ensureQueuesAndProfiles(String region) {
    if (store == null) return;
    String r = normalizeRegion(region);
    Map<String, Object> inner = loadInnerPipelinesForEnsure(r);
    if (inner == null || inner.isEmpty()) {
      return;
    }
    try {
      if (store.getQueues(r) == null) {
        Map<String, Object> doc = loadSectionDocumentFromDb(r, SECTION_QUEUES);
        if (doc == null || doc.isEmpty()) {
          doc = loadQueuesJsonFromPipelineTemplate(r);
        }
        if (doc == null || doc.isEmpty()) {
          doc = extractQueuesMap(inner);
        }
        store.putQueues(r, doc);
        log.info("Stored Redis queues section for region={}", r);
      }
      if (store.getProfiles(r) == null) {
        Map<String, Object> doc = loadSectionDocumentFromDb(r, SECTION_PROFILES);
        if (doc == null || doc.isEmpty()) {
          doc = loadProfilesJsonFromPipelineTemplate(r);
        }
        if (doc == null || doc.isEmpty()) {
          doc = extractStandaloneProfilesDocument(inner);
        }
        store.putProfiles(r, doc != null ? doc : Map.of());
        log.info("Stored Redis profiles section for region={}", r);
      }
    } catch (Exception e) {
      log.warn("Failed to ensure queues/profiles Redis keys for region={}: {}", r, e.getMessage());
    }
  }

  /** Inner pipeline id → document when pipelines exist in Redis or DB. */
  private Map<String, Object> loadInnerPipelinesForEnsure(String region) {
    Map<String, Object> raw = store.getPipelines(region);
    if (raw != null) {
      return PipelineRegistry.unwrapPipelinesRoot(raw);
    }
    if (!jdbcUrl.isEmpty()) {
      return loadInnerPipelinesFromDb(region);
    }
    return null;
  }

  private Map<String, Object> loadQueuesJsonFromPipelineTemplate(String region) {
    return loadColumnDocumentFromPipelineTemplate(region, "queues_json");
  }

  private Map<String, Object> loadProfilesJsonFromPipelineTemplate(String region) {
    return loadColumnDocumentFromPipelineTemplate(region, "profiles_json");
  }

  /** Reads first non-null column value for the region (full Redis document shape). */
  private Map<String, Object> loadColumnDocumentFromPipelineTemplate(String region, String column) {
    if (jdbcUrl.isEmpty()) return null;
    final String sql = "SELECT " + column + " FROM olo_pipeline_template WHERE region = ? AND is_active = true AND "
        + column + " IS NOT NULL ORDER BY pipeline_id";
    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, region);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return jsonbToMap(rs, 1);
        }
      }
    } catch (Exception e) {
      log.debug("Could not load {} from olo_pipeline_template for region={}: {}", column, region, e.getMessage());
    }
    return null;
  }

  private static Map<String, Object> jsonbToMap(ResultSet rs, int columnIndex) throws Exception {
    String json = rs.getString(columnIndex);
    if (json == null || json.isBlank()) {
      Object o = rs.getObject(columnIndex);
      if (o != null) {
        json = o.toString();
      }
    }
    if (json == null || json.isBlank()) return null;
    JsonNode node = MAPPER.readTree(json);
    if (!node.isObject()) return null;
    return MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> loadSectionDocumentFromDb(String region, String sectionName) {
    if (jdbcUrl.isEmpty()) return null;
    final String sql = """
        SELECT json_document
        FROM olo_config_section
        WHERE region = ? AND section_name = ?
        """;
    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, region);
      ps.setString(2, sectionName);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return null;
        return jsonbToMap(rs, 1);
      }
    } catch (Exception e) {
      log.debug("No olo_config_section row for region={} section={}: {}", region, sectionName, e.getMessage());
      return null;
    }
  }

  static Map<String, Object> extractStandaloneProfilesDocument(Map<String, Object> innerPipelines) {
    for (Object value : innerPipelines.values()) {
      Map<String, Object> root = toStringKeyMap(value);
      if (root == null) continue;
      Object cp = root.get("chatProfiles");
      Map<String, Object> chat = toStringKeyMap(cp);
      if (chat == null) continue;
      Object profs = chat.get("profiles");
      if (!(profs instanceof Map<?, ?> pm) || pm.isEmpty()) continue;
      return new LinkedHashMap<>(chat);
    }
    return null;
  }

  static Map<String, Object> extractQueuesMap(Map<String, Object> innerPipelines) {
    Map<String, Object> queues = new LinkedHashMap<>();
    for (Object value : innerPipelines.values()) {
      Map<String, Object> root = toStringKeyMap(value);
      if (root == null) continue;
      Object cp = root.get("chatProfiles");
      Map<String, Object> chat = toStringKeyMap(cp);
      if (chat == null) continue;
      Object profs = chat.get("profiles");
      if (!(profs instanceof Map<?, ?>)) continue;
      for (Object p : ((Map<?, ?>) profs).values()) {
        Map<String, Object> prof = toStringKeyMap(p);
        if (prof == null) continue;
        Object q = prof.get("queue");
        if (q == null) continue;
        String name = q.toString().trim();
        if (name.isEmpty()) continue;
        queues.putIfAbsent(name, Map.of("workerType", "default"));
      }
    }
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("queues", queues);
    return envelope;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> toStringKeyMap(Object value) {
    if (value == null) return null;
    if (value instanceof Map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
        if (e.getKey() != null) {
          out.put(String.valueOf(e.getKey()), e.getValue());
        }
      }
      return out;
    }
    if (value instanceof JsonNode node && node.isObject()) {
      return MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }
    return null;
  }

  private static String normalizeRegion(String region) {
    return region == null || region.isBlank() ? "default" : region.trim();
  }

  private Map<String, Object> loadInnerPipelinesFromDb(String region) {
    final String sql = """
        SELECT pipeline_id, tree_json
        FROM olo_pipeline_template
        WHERE region = ? AND is_active = true
        ORDER BY pipeline_id
        """;
    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, region);
      try (ResultSet rs = ps.executeQuery()) {
        Map<String, Object> out = new LinkedHashMap<>();
        while (rs.next()) {
          String pipelineId = rs.getString(1);
          String treeJson = rs.getString(2);
          if (pipelineId == null || pipelineId.isBlank()) continue;
          if (treeJson == null || treeJson.isBlank()) continue;
          JsonNode node = MAPPER.readTree(treeJson);
          out.put(pipelineId, node);
        }
        return out;
      }
    } catch (Exception e) {
      log.warn("Failed to load active pipelines from DB for region={}: {}", region, e.getMessage());
      return null;
    }
  }
}
