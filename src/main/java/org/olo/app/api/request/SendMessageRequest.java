/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api.request;

import jakarta.validation.constraints.NotBlank;

public class SendMessageRequest {

    @NotBlank
    private String content;

    private String model;
    private Double temperature;
    private Boolean ragEnabled;

    /** Indexed knowledge source / capability source for RAG-grounded chat. */
    private String capabilitySource;

    /** Task queue / pipeline from frontend; overrides backend default when set. */
    private String taskQueue;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Boolean getRagEnabled() { return ragEnabled; }
    public void setRagEnabled(Boolean ragEnabled) { this.ragEnabled = ragEnabled; }

    public String getCapabilitySource() { return capabilitySource; }
    public void setCapabilitySource(String capabilitySource) { this.capabilitySource = capabilitySource; }

    public String getTaskQueue() { return taskQueue; }
    public void setTaskQueue(String taskQueue) { this.taskQueue = taskQueue; }
}
