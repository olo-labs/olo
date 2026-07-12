/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.validation;

import java.util.List;

/**
 * Outcome of {@link WorkflowValidator#validate(org.olo.definition.workflow.WorkflowDefinition)}.
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, List.copyOf(errors));
    }
}
