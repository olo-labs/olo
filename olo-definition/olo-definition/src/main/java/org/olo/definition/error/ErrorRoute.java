/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Routes execution to another node when retries are exhausted or routing is used without retry.
 */
@JsonDeserialize(builder = ErrorRoute.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ErrorRoute {

    private final String targetNodeId;
    private final String targetPort;

    private ErrorRoute(Builder builder) {
        this.targetNodeId = builder.targetNodeId;
        this.targetPort = builder.targetPort;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getTargetPort() {
        return targetPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ErrorRoute that)) {
            return false;
        }
        return Objects.equals(targetNodeId, that.targetNodeId) && Objects.equals(targetPort, that.targetPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetNodeId, targetPort);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String targetNodeId;
        private String targetPort;

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder targetPort(String targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public ErrorRoute build() {
            Objects.requireNonNull(targetNodeId, "targetNodeId is required");
            return new ErrorRoute(this);
        }
    }
}
