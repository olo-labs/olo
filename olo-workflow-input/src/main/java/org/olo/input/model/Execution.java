/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Execution controls for the workflow run: callback delivery and timeout.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Execution {

    private final String callbackUrl;
    private final Integer timeoutSeconds;

    @JsonCreator
    public Execution(
            @JsonProperty("callbackUrl") String callbackUrl,
            @JsonProperty("timeoutSeconds") Integer timeoutSeconds) {
        this.callbackUrl = callbackUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Execution execution = (Execution) o;
        return Objects.equals(callbackUrl, execution.callbackUrl)
                && Objects.equals(timeoutSeconds, execution.timeoutSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callbackUrl, timeoutSeconds);
    }
}
