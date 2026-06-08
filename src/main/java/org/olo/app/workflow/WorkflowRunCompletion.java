/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.workflow;

/**
 * Callback for asynchronous chat workflow completion (Temporal result or failure).
 */
public interface WorkflowRunCompletion {

    void onCompleted(String workflowResult);

    void onFailed(String errorMessage);
}
