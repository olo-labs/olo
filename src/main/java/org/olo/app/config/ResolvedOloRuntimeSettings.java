/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.olo.definition.workflow.WorkflowDefinition;

/**
 * Runtime routing resolved from {@code olo.configuration.dir} workflow definitions
 * (e.g. {@code default/workflow.json}: {@code queue}, {@code workflowType}, folder → tenant).
 */
public record ResolvedOloRuntimeSettings(
        String tenantId,
        String taskQueue,
        String workflowType,
        String temporalNamespace) {

    private static final String FALLBACK_TENANT = "default";
    private static final String FALLBACK_QUEUE = "olo-chat";
    private static final String FALLBACK_WORKFLOW_TYPE = "olo";
    private static final String FALLBACK_NAMESPACE = "default";

    public static ResolvedOloRuntimeSettings resolve(RegionalConfigurationRegistry registry) {
        String tenantId = FALLBACK_TENANT;
        String taskQueue = FALLBACK_QUEUE;
        String workflowType = FALLBACK_WORKFLOW_TYPE;

        if (registry != null && registry.isLoaded()) {
            String region = registry.getDefaultRegion();
            if (region != null && !region.isBlank()) {
                tenantId = region.trim();
            }
            RegionalConfigurationSnapshot snap = registry.get(region);
            if (snap != null && !snap.getWorkflows().isEmpty()) {
                WorkflowDefinition workflow = snap.getWorkflows().get(0);
                if (workflow.getQueue() != null && !workflow.getQueue().isBlank()) {
                    taskQueue = workflow.getQueue().trim();
                }
                if (workflow.getWorkflowType() != null && !workflow.getWorkflowType().isBlank()) {
                    workflowType = workflow.getWorkflowType().trim();
                }
            }
        }

        return new ResolvedOloRuntimeSettings(tenantId, taskQueue, workflowType, FALLBACK_NAMESPACE);
    }
}
