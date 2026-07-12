/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api.request;

/**
 * Request body for POST /api/runs/{runId}/human-input (user approval or text).
 */
public class HumanInputRequest {

    private boolean approved = true;
    private String message;  // optional text response
    /** Optional text stored in chat history (may differ from workflow {@link #message}, e.g. JSON payload). */
    private String historyText;

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getHistoryText() { return historyText; }
    public void setHistoryText(String historyText) { this.historyText = historyText; }
}
