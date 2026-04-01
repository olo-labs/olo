/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateRunRequest {

    @NotBlank
    private String tenantId;

    @NotNull
    @Valid
    private RunInput input;

    /** Task queue / pipeline from frontend; overrides backend default when set. */
    private String taskQueue;

    /** Execution versioning for diff: which workflow/model/planner produced this run. */
    private String workflowVersion;
    private String modelVersion;
    private String plannerVersion;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public RunInput getInput() { return input; }
    public void setInput(RunInput input) { this.input = input; }

    public String getTaskQueue() { return taskQueue; }
    public void setTaskQueue(String taskQueue) { this.taskQueue = taskQueue; }

    public String getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(String workflowVersion) { this.workflowVersion = workflowVersion; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getPlannerVersion() { return plannerVersion; }
    public void setPlannerVersion(String plannerVersion) { this.plannerVersion = plannerVersion; }

    public static class RunInput {
        @NotBlank
        private String type;  // e.g. "chat"

        private String message;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
