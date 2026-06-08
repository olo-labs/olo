/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.temporal.sdk;

/**
 * Raised when a chat workflow fails or cannot be awaited on the Temporal client.
 */
public class WorkflowExecutionException extends Exception {

    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkflowExecutionException(Throwable cause) {
        super(cause);
    }
}
