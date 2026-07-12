/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.validation;

import java.util.List;

/**
 * Thrown when a workflow fails structural validation.
 */
public class WorkflowValidationException extends IllegalArgumentException {

    private final List<String> errors;

    public WorkflowValidationException(List<String> errors) {
        super("Workflow validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
