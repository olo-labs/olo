/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration;

/**
 * Feature flags over multilayered configuration. Hierarchy: global → tenant → pipeline.
 * Final flag value = merged result (later scope overrides earlier).
 * <p>
 * Use {@link #isEnabled(String, String, String)} for tenant + pipeline scope, or
 * {@link #isEnabled(String)} for global-only. Pipeline/resource IDs use <em>type:name</em> form (e.g. {@code pipeline:chat}).
 * </p>
 */
public final class FeatureFlags {

  /** Streaming enabled. */
  public static final String STREAMING_ENABLED = "olo.feature.streaming.enabled";
  /** Tool execution. */
  public static final String TOOL_EXECUTION = "olo.feature.tool_execution";
  /** Pipeline cache. */
  public static final String PIPELINE_CACHE = "olo.feature.pipeline_cache";
  /** Dynamic model selection. */
  public static final String DYNAMIC_MODEL_SELECTION = "olo.feature.dynamic_model_selection";
  /** Pipeline debug. */
  public static final String PIPELINE_DEBUG = "olo.feature.pipeline_debug";

  private FeatureFlags() {}

  /**
   * Global-only: is the flag enabled (no tenant/pipeline override)?
   */
  public static boolean isEnabled(String flagKey) {
    return isEnabled(flagKey, null, null);
  }

  /**
   * For tenant scope: global + tenant merged.
   */
  public static boolean isEnabled(String flagKey, String tenantId) {
    return isEnabled(flagKey, tenantId, null);
  }

  /**
   * Full scope: global → tenant → pipeline. Pipeline maps to config resource (e.g. pipeline id).
   *
   * @param flagKey   e.g. {@link #STREAMING_ENABLED}
   * @param tenantId  tenant id, or null for global-only
   * @param pipelineId pipeline/resource id in type:name form (e.g. pipeline:chat), or null to stop at tenant
   */
  public static boolean isEnabled(String flagKey, String tenantId, String pipelineId) {
    Configuration ctx = (pipelineId != null || (tenantId != null && !tenantId.isBlank()))
        ? ConfigurationProvider.forContext(tenantId, pipelineId)
        : ConfigurationProvider.get();
    return ctx != null && ctx.getBoolean(flagKey, false);
  }
}
