/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Tenant, roles, permissions, session, and execution context for the workflow.
 * runId, callbackBaseUrl, and correlationId are set by the backend when starting the workflow.
 */
public final class Context {

    private final String tenantId;
    private final String groupId;
    private final List<String> roles;
    private final List<String> permissions;
    private final String sessionId;
    private final String runId;
    private final String callbackBaseUrl;
    private final String correlationId;

    @JsonCreator
    public Context(
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("permissions") List<String> permissions,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("runId") String runId,
            @JsonProperty("callbackBaseUrl") String callbackBaseUrl,
            @JsonProperty("correlationId") String correlationId) {
        this.tenantId = tenantId;
        this.groupId = groupId;
        this.roles = roles != null ? List.copyOf(roles) : List.of();
        this.permissions = permissions != null ? List.copyOf(permissions) : List.of();
        this.sessionId = sessionId;
        this.runId = runId != null ? runId : "";
        this.callbackBaseUrl = callbackBaseUrl != null ? callbackBaseUrl : "";
        this.correlationId = correlationId != null ? correlationId : "";
    }

    /** Convenience constructor without runId/callbackBaseUrl/correlationId (e.g. for producer examples). */
    public Context(String tenantId, String groupId, List<String> roles, List<String> permissions, String sessionId) {
        this(tenantId, groupId, roles, permissions, sessionId, null, null, null);
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getGroupId() {
        return groupId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Context context = (Context) o;
        return Objects.equals(tenantId, context.tenantId)
                && Objects.equals(groupId, context.groupId)
                && Objects.equals(roles, context.roles)
                && Objects.equals(permissions, context.permissions)
                && Objects.equals(sessionId, context.sessionId)
                && Objects.equals(runId, context.runId)
                && Objects.equals(callbackBaseUrl, context.callbackBaseUrl)
                && Objects.equals(correlationId, context.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, groupId, roles, permissions, sessionId, runId, callbackBaseUrl, correlationId);
    }
}
