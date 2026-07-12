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
 * Per-node failure handling: optional retry then optional fallback route.
 * Serialized as {@code onFailure} on {@link org.olo.definition.node.NodeDefinition}.
 */
@JsonDeserialize(builder = OnFailureDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class OnFailureDefinition {

    private final RetryPolicy retry;
    private final ErrorRoute route;

    private OnFailureDefinition(Builder builder) {
        this.retry = builder.retry;
        this.route = builder.route;
    }

    public static Builder builder() {
        return new Builder();
    }

    public RetryPolicy getRetry() {
        return retry;
    }

    public ErrorRoute getRoute() {
        return route;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OnFailureDefinition that)) {
            return false;
        }
        return Objects.equals(retry, that.retry) && Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(retry, route);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private RetryPolicy retry;
        private ErrorRoute route;

        public Builder retry(RetryPolicy retry) {
            this.retry = retry;
            return this;
        }

        public Builder route(ErrorRoute route) {
            this.route = route;
            return this;
        }

        public OnFailureDefinition build() {
            return new OnFailureDefinition(this);
        }
    }
}
