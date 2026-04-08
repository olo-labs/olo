/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Optional {@code chatProfiles} block on a regional pipeline definition (same JSON document as
 * {@code executionTree}, etc.). Deserialized only from pipeline configuration — not from flattened core config.
 * Also used for the standalone Redis document at {@code olo:config:profiles:&lt;region&gt;} (same shape at root).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PipelineChatProfilesSection(
    @JsonProperty("profileOrder") @JsonAlias("profile_order") String profileOrder,
    @JsonProperty("profiles") Map<String, ChatProfile> profiles) {

  public PipelineChatProfilesSection {
    profiles = profiles == null ? Map.of() : Map.copyOf(profiles);
  }
}
