/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.olo.definition.workflow.WorkflowDefinition;

import java.util.List;

/**
 * Regional worker/UI configuration: {@link WorkflowDefinition} artifacts from {@code olo.configuration.dir}.
 */
public final class RegionalConfigurationSnapshot {

    private final String region;
    private final List<WorkflowDefinition> workflows;

    public RegionalConfigurationSnapshot(String region, List<WorkflowDefinition> workflows) {
        this.region = region;
        this.workflows = workflows == null ? List.of() : List.copyOf(workflows);
    }

    public String getRegion() {
        return region;
    }

    public List<WorkflowDefinition> getWorkflows() {
        return workflows;
    }
}
