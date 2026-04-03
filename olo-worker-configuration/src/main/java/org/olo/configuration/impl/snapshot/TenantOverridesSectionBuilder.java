/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import org.olo.configuration.snapshot.ConfigurationSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Worker/bootstrap fallback: builds tenant override cache from DB table {@code olo_tenant_pipeline_override}
 * and stores it in Redis as keys {@code <root>:config:overrides:tenant:<tenantId>}.
 *
 * This exists to support the rule: tenant overrides in DB → cached in Redis so workers can read
 * {@link ConfigurationSnapshotStore#getTenantOverrides(String)} without touching DB.
 */
public final class TenantOverridesSectionBuilder {

  private static final Logger log = LoggerFactory.getLogger(TenantOverridesSectionBuilder.class);

  private final String jdbcUrl;
  private final String username;
  private final String password;
  private final ConfigurationSnapshotStore store;

  public TenantOverridesSectionBuilder(String jdbcUrl, String username, String password, ConfigurationSnapshotStore store) {
    this.jdbcUrl = jdbcUrl == null ? "" : jdbcUrl.trim();
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password.trim();
    this.store = store;
  }

  /**
   * Loads all tenant overrides from DB and stores them in Redis as
   * {@code <root>:config:overrides:tenant:<tenantId>} keys.
   * Safe to run when table is empty; on error, logs and returns.
   */
  public void buildAndStoreAllTenants() {
    if (store == null || jdbcUrl.isEmpty()) return;
    Map<String, Map<String, Object>> byTenant = loadAllTenantOverrides();
    if (byTenant == null || byTenant.isEmpty()) return;
    try {
      for (Map.Entry<String, Map<String, Object>> e : byTenant.entrySet()) {
        String tenantId = e.getKey();
        Map<String, Object> overrides = e.getValue();
        if (tenantId == null || tenantId.isBlank() || overrides == null || overrides.isEmpty()) continue;
        store.putTenantOverrides(tenantId, overrides);
      }
      log.info("Built and stored tenant overrides for {} tenants", byTenant.size());
    } catch (Exception e) {
      log.warn("Failed to store tenant overrides to Redis: {}", e.getMessage());
    }
  }

  /**
   * DB query: select all tenant overrides; produce a map
   * tenantId -> (pipelineId:vVersion -> { overrideJson, updatedAt }).
   */
  private Map<String, Map<String, Object>> loadAllTenantOverrides() {
    final String sql = """
        SELECT tenant_id, pipeline_id, pipeline_version, override_json, updated_at
        FROM olo_tenant_pipeline_override
        ORDER BY tenant_id, pipeline_id, pipeline_version
        """;
    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
         PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      Map<String, Map<String, Object>> out = new LinkedHashMap<>();
      while (rs.next()) {
        String tenantId = rs.getString(1);
        String pipelineId = rs.getString(2);
        long ver = rs.getLong(3);
        Object override = rs.getObject(4);
        Object updatedAt = rs.getObject(5);
        if (tenantId == null || tenantId.isBlank() || pipelineId == null || pipelineId.isBlank()) {
          continue;
        }
        String key = pipelineId + ":v" + ver;

        @SuppressWarnings("unchecked")
        Map<String, Object> tenantMap = (Map<String, Object>) out.computeIfAbsent(tenantId, k -> new LinkedHashMap<>());
        Map<String, Object> o = new LinkedHashMap<>();
        if (override != null) o.put("overrideJson", override.toString());
        if (updatedAt != null) o.put("updatedAt", updatedAt.toString());
        tenantMap.put(key, o);
      }
      return out;
    } catch (Exception e) {
      log.warn("Failed to load tenant overrides from DB: {}", e.getMessage());
      return null;
    }
  }
}

