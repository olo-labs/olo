/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api.response;

public class CreateRunResponse {

    private String runId;

    public CreateRunResponse() {}

    public CreateRunResponse(String runId) {
        this.runId = runId;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
}
