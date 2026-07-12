/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {
    QUESTION_ANSWER,
    AGENT_EXECUTION,
    WORKFLOW_RUN;

    @JsonValue
    public String toValue() {
        return name();
    }
}
