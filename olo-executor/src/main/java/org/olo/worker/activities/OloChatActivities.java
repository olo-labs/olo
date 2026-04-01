/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.worker.activities;

import io.temporal.activity.ActivityMethod;

import java.util.Map;

/**
 * Activities for Olo chat: report event to Chat BE, planner, tool, model.
 */
public interface OloChatActivities {

    @ActivityMethod
    void reportEvent(String runId, String callbackBaseUrl, long sequenceNumber, String eventType, String correlationId,
                     String nodeId, String parentNodeId, String nodeType, String status,
                     Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata);

    @ActivityMethod
    String planner(String userMessage, String stepsDone);

    @ActivityMethod
    Map<String, Object> runTool(String toolName, Map<String, Object> params);

    @ActivityMethod
    String runModel(String userMessage, String toolResultOrNull);
}
