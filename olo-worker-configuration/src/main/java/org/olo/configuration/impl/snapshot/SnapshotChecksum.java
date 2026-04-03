/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.impl.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.olo.configuration.snapshot.ConfigurationSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes SHA-256 checksum for sectioned snapshots using canonical JSON.
 */
public final class SnapshotChecksum {

  private static final ObjectMapper CANONICAL_JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private SnapshotChecksum() {}

  public static String compute(ConfigurationSnapshot core, Map<String, Object> pipelines, Map<String, Object> connections) {
    if (core == null) return null;
    try {
      String coreJson = CANONICAL_JSON.writeValueAsString(canonicalize(coreToMap(core)));
      String pipelinesJson = CANONICAL_JSON.writeValueAsString(canonicalize(pipelines == null ? Map.of() : pipelines));
      String connectionsJson = CANONICAL_JSON.writeValueAsString(canonicalize(connections == null ? Map.of() : connections));
      return sha256Hex(coreJson + pipelinesJson + connectionsJson);
    } catch (Exception e) {
      return null;
    }
  }

  private static Map<String, Object> coreToMap(ConfigurationSnapshot core) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("version", core.getVersion());
    root.put("lastUpdated", core.getLastUpdated() != null ? core.getLastUpdated().toString() : null);
    root.put("global", core.getGlobalConfig());
    if (!core.getRegionConfig().isEmpty()) root.put("region", core.getRegionConfig());
    root.put("tenantOverrides", core.getTenantConfig());
    if (!core.getResourceConfig().isEmpty()) root.put("resourceOverrides", core.getResourceConfig());
    return root;
  }

  @SuppressWarnings("unchecked")
  private static Object canonicalize(Object value) {
    if (value instanceof Map) {
      Map<String, Object> sorted = new TreeMap<>();
      Map<?, ?> map = (Map<?, ?>) value;
      for (Map.Entry<?, ?> e : map.entrySet()) {
        if (e.getKey() != null) {
          sorted.put(String.valueOf(e.getKey()), canonicalize(e.getValue()));
        }
      }
      return sorted;
    }
    if (value instanceof List) {
      List<Object> out = new ArrayList<>();
      for (Object item : (List<Object>) value) {
        out.add(canonicalize(item));
      }
      return out;
    }
    return value;
  }

  private static String sha256Hex(String data) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
