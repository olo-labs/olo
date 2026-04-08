/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One chat UI profile: display label, optional short summary, plus Temporal queue and pipeline ids.
 * Used inside pipeline JSON {@code chatProfiles.profiles} and (legacy) flattened core config.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatProfile(
    @JsonProperty("display_name") @JsonAlias("displayName") String displayName,
    @JsonProperty("display_summary") @JsonAlias("displaySummary") String displaySummary,
    @JsonProperty("emoji") String emoji,
    String queue,
    String pipeline,
    /** When true, profile appears in per-message “run again” UI (pipeline JSON {@code run_again}). */
    @JsonProperty("run_again") @JsonAlias("runAgain") boolean runAgain) {

  @JsonCreator
  public ChatProfile {
    displayName = displayName == null ? "" : displayName.trim();
    displaySummary = displaySummary == null ? "" : displaySummary.trim();
    emoji = emoji == null ? "" : emoji.trim();
    queue = queue == null ? "" : queue.trim();
    pipeline = pipeline == null ? "" : pipeline.trim();
  }
}
