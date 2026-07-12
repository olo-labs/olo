/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.validation;

import java.util.List;

/**
 * Result of validating a worker {@link org.olo.input.model.WorkflowInput} against a workflow definition.
 */
public record WorkflowInvocationValidationResult(boolean valid, List<String> errors) {

    public static WorkflowInvocationValidationResult success() {
        return new WorkflowInvocationValidationResult(true, List.of());
    }

    public static WorkflowInvocationValidationResult failure(List<String> errors) {
        return new WorkflowInvocationValidationResult(false, List.copyOf(errors));
    }
}
