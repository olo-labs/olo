/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.workflow;

import org.olo.input.model.WorkflowInput;

/**
 * Backend abstraction for starting and signaling Olo chat workflows.
 * Implementations delegate to {@code olo-temporal-sdk}; application code must not use Temporal SDK types directly.
 */
public interface WorkflowRunner {

    /**
     * Starts a chat workflow run. {@code completion} is invoked asynchronously when Temporal reports done or failed.
     */
    void startChatRun(
            String runId,
            WorkflowInput workflowInput,
            String taskQueue,
            WorkflowRunCompletion completion);

    void signalHumanInput(String runId, boolean approved, String message);
}
