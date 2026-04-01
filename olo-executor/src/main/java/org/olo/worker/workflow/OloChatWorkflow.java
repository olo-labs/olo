/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.worker.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowMethod;

/**
 * Olo chat workflow: planner → optional tool → model → optional human → final answer.
 * Receives WorkflowInput (JSON type); Temporal serializes it as a JSON object.
 */
public interface OloChatWorkflow {

    @WorkflowMethod
    String execute(org.olo.input.model.WorkflowInput workflowInput);

    @SignalMethod
    void humanInput(boolean approved, String message);
}
