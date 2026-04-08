/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deserializes chat profile definitions from flattened effective configuration (see {@link ChatProfile})
 * and optional display order {@value #PROFILE_ORDER_KEY}.
 */
public final class ChatProfiles {

  /** Prefix for flattened keys: {@code olo.chat.profiles.<profileId>.display_name|display_summary|emoji|queue|pipeline}. */
  public static final String CONFIG_PREFIX = "olo.chat.profiles.";

  /** Comma-separated profile ids for UI ordering (e.g. {@code fast,smart,cheap,debug}). */
  public static final String PROFILE_ORDER_KEY = "olo.chat.profileOrder";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ChatProfiles() {}

  /**
   * Parses profiles from a map produced by {@code olo_config_resource} JSON flattening
   * (e.g. {@code olo.chat.profiles.fast.queue} → {@code olo-fast-queue}).
   */
  public static Map<String, ChatProfile> fromEffectiveConfig(Map<String, String> flat) {
    if (flat == null || flat.isEmpty()) {
      return Map.of();
    }
    Map<String, Partial> partial = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : flat.entrySet()) {
      String key = e.getKey();
      if (key == null || !key.startsWith(CONFIG_PREFIX)) {
        continue;
      }
      String rest = key.substring(CONFIG_PREFIX.length());
      int dot = rest.indexOf('.');
      if (dot <= 0) {
        continue;
      }
      String id = rest.substring(0, dot);
      String field = rest.substring(dot + 1);
      partial.computeIfAbsent(id, k -> new Partial()).put(field, e.getValue());
    }
    Map<String, ChatProfile> out = new LinkedHashMap<>();
    for (Map.Entry<String, Partial> e : partial.entrySet()) {
      Partial p = e.getValue();
      if (p.queue.isEmpty() || p.pipeline.isEmpty()) {
        continue;
      }
      String label = p.displayName.isEmpty() ? e.getKey() : p.displayName;
      out.put(e.getKey(), new ChatProfile(label, p.displaySummary, p.emoji, p.queue, p.pipeline, p.runAgain));
    }
    return Map.copyOf(out);
  }

  /**
   * Ordered list of (id, profile) for APIs: first {@code profileOrder} keys that exist, then remaining ids sorted.
   */
  public static List<NamedChatProfile> orderedNamed(Map<String, ChatProfile> profiles, String orderCsv) {
    if (profiles == null || profiles.isEmpty()) {
      return List.of();
    }
    List<String> order = parseOrder(orderCsv);
    List<NamedChatProfile> out = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String id : order) {
      ChatProfile p = profiles.get(id);
      if (p != null) {
        out.add(new NamedChatProfile(id, p));
        seen.add(id);
      }
    }
    List<String> rest = new ArrayList<>();
    for (String id : profiles.keySet()) {
      if (!seen.contains(id)) {
        rest.add(id);
      }
    }
    Collections.sort(rest);
    for (String id : rest) {
      out.add(new NamedChatProfile(id, profiles.get(id)));
    }
    return List.copyOf(out);
  }

  /**
   * UI profiles from a pipeline's {@code chatProfiles} section ({@link PipelineChatProfilesSection}).
   */
  public static List<NamedChatProfile> fromPipelineSection(PipelineChatProfilesSection section) {
    if (section == null || section.profiles() == null || section.profiles().isEmpty()) {
      return List.of();
    }
    return orderedNamed(section.profiles(), section.profileOrder());
  }

  /** Parses a JSON object {@code { "fast": { "display_name": ... }, ... }} (e.g. tests or raw documents). */
  public static Map<String, ChatProfile> parseJsonDocument(String json) throws java.io.IOException {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    return MAPPER.readValue(json.trim(), new TypeReference<Map<String, ChatProfile>>() {});
  }

  private static List<String> parseOrder(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static final class Partial {
    private String displayName = "";
    private String displaySummary = "";
    private String emoji = "";
    private String queue = "";
    private String pipeline = "";
    private boolean runAgain = false;

    void put(String field, String value) {
      if (value == null) {
        return;
      }
      String v = value.trim();
      switch (field) {
        case "display_name" -> displayName = v;
        case "display_summary" -> displaySummary = v;
        case "emoji" -> emoji = v;
        case "queue" -> queue = v;
        case "pipeline" -> pipeline = v;
        case "run_again" -> runAgain = "true".equalsIgnoreCase(v) || "1".equals(v);
        default -> { }
      }
    }
  }
}
