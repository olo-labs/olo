/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.chat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatProfilesTest {

  @Test
  void fromEffectiveConfig_flattensProfiles() {
    Map<String, String> flat = new LinkedHashMap<>();
    flat.put("olo.chat.profileOrder", "fast,smart");
    flat.put("olo.chat.profiles.fast.display_name", "Fast Response");
    flat.put("olo.chat.profiles.fast.display_summary", "Quick path.");
    flat.put("olo.chat.profiles.fast.emoji", "⚡");
    flat.put("olo.chat.profiles.fast.queue", "olo-fast-queue");
    flat.put("olo.chat.profiles.fast.pipeline", "fast-pipeline");
    flat.put("olo.chat.profiles.smart.display_name", "Smart Assistant");
    flat.put("olo.chat.profiles.smart.queue", "olo-smart-queue");
    flat.put("olo.chat.profiles.smart.pipeline", "reasoning-pipeline");

    Map<String, ChatProfile> m = ChatProfiles.fromEffectiveConfig(flat);
    assertEquals(2, m.size());
    assertEquals("Quick path.", m.get("fast").displaySummary());
    assertEquals("⚡", m.get("fast").emoji());
    assertEquals("olo-fast-queue", m.get("fast").queue());
    assertEquals("fast-pipeline", m.get("fast").pipeline());
    List<NamedChatProfile> ordered = ChatProfiles.orderedNamed(m, flat.get(ChatProfiles.PROFILE_ORDER_KEY));
    assertEquals(2, ordered.size());
    assertEquals("fast", ordered.get(0).id());
    assertEquals("smart", ordered.get(1).id());
  }

  @Test
  void parseJsonDocument_readsDisplayName() throws Exception {
    String json =
        """
        {
          "fast": { "display_name": "Fast Response", "display_summary": "Short blurb.", "emoji": "⚡", "queue": "q1", "pipeline": "p1" }
        }
        """;
    Map<String, ChatProfile> m = ChatProfiles.parseJsonDocument(json);
    assertEquals("Fast Response", m.get("fast").displayName());
    assertEquals("Short blurb.", m.get("fast").displaySummary());
    assertEquals("⚡", m.get("fast").emoji());
    assertEquals("q1", m.get("fast").queue());
    assertFalse(m.get("fast").runAgain());
  }

  @Test
  void parseJsonDocument_readsRunAgain() throws Exception {
    String json =
        """
        {
          "fast": { "display_name": "Fast", "queue": "q1", "pipeline": "p1", "run_again": true }
        }
        """;
    Map<String, ChatProfile> m = ChatProfiles.parseJsonDocument(json);
    assertTrue(m.get("fast").runAgain());
  }

  @Test
  void skipsIncompleteProfile() {
    Map<String, String> flat = Map.of("olo.chat.profiles.broken.display_name", "X");
    assertTrue(ChatProfiles.fromEffectiveConfig(flat).isEmpty());
  }

  @Test
  void fromPipelineSection_ordersProfiles() {
    Map<String, ChatProfile> prof =
        Map.of(
            "fast", new ChatProfile("Fast", "", "", "q1", "p1", false),
            "slow", new ChatProfile("Slow", "", "", "q2", "p2", false));
    PipelineChatProfilesSection section = new PipelineChatProfilesSection("slow,fast", prof);
    List<NamedChatProfile> named = ChatProfiles.fromPipelineSection(section);
    assertEquals(2, named.size());
    assertEquals("slow", named.get(0).id());
    assertEquals("fast", named.get(1).id());
  }
}
