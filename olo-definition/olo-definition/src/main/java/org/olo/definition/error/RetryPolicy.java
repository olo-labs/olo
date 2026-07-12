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
 * Declarative retry behavior when a node fails. Interpreted by runtime, not executed in this module.
 */
@JsonDeserialize(builder = RetryPolicy.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RetryPolicy {

    private final int attempts;
    private final Long initialDelayMs;
    private final Long maxDelayMs;

    private RetryPolicy(Builder builder) {
        this.attempts = builder.attempts;
        this.initialDelayMs = builder.initialDelayMs;
        this.maxDelayMs = builder.maxDelayMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getAttempts() {
        return attempts;
    }

    public Long getInitialDelayMs() {
        return initialDelayMs;
    }

    public Long getMaxDelayMs() {
        return maxDelayMs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RetryPolicy that)) {
            return false;
        }
        return attempts == that.attempts
                && Objects.equals(initialDelayMs, that.initialDelayMs)
                && Objects.equals(maxDelayMs, that.maxDelayMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attempts, initialDelayMs, maxDelayMs);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private int attempts = 1;
        private Long initialDelayMs;
        private Long maxDelayMs;

        public Builder attempts(int attempts) {
            this.attempts = attempts;
            return this;
        }

        public Builder initialDelayMs(Long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder maxDelayMs(Long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}
