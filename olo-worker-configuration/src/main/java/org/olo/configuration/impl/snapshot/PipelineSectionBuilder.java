/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Worker bootstrap fallback only: builds the pipelines section from DB table {@code olo_pipeline_template}
 * (active templates for the region), then stores it in Redis (pipelines key + meta update).
 * <p>
 * This exists to support the rule: read pipelines from Redis; if missing, load from DB, write to Redis,
 * then consume from Redis.
 * </p>
 */
public final class PipelineSectionBuilder {

  private static final Logger log = LoggerFactory.getLogger(PipelineSectionBuilder.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

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
   * Loads all active pipelines for region from DB and stores them in Redis pipelines section.
   * Returns the pipelines map (possibly empty) or null on error.
   */
  public Map<String, Object> buildAndStore(String region) {
    if (store == null) return null;
    String r = region == null || region.isBlank() ? "default" : region.trim();
    if (jdbcUrl.isEmpty()) return null;

    Map<String, Object> pipelines = loadActivePipelines(r);
    if (pipelines == null) return null;
    try {
      store.putPipelines(r, pipelines);
      log.info("Built and stored pipelines section for region={} pipelines={}", r, pipelines.size());
      return pipelines;
    } catch (Exception e) {
      log.warn("Failed to store pipelines section to Redis for region={}: {}", r, e.getMessage());
      return null;
    }
  }

  /**
   * DB query: select active pipelines for region; produce a map pipelineId -> pipelineJsonObject.
   * Stored in Redis as a JSON object with pipeline IDs as top-level keys.
   */
  private Map<String, Object> loadActivePipelines(String region) {
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

