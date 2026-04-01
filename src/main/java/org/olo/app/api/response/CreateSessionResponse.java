/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.api.response;

public class CreateSessionResponse {

    private String sessionId;

    public CreateSessionResponse() {}
    public CreateSessionResponse(String sessionId) { this.sessionId = sessionId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
