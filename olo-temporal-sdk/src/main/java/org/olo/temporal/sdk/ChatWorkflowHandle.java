/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.temporal.sdk;

import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowStub;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Handle for a started chat workflow run. Await completion synchronously or on an executor.
 */
public final class ChatWorkflowHandle {

    private final WorkflowStub stub;

    ChatWorkflowHandle(WorkflowStub stub) {
        this.stub = stub;
    }

    /**
     * Blocks until the workflow completes and returns its String result (may be null or blank).
     */
    public String awaitResult() throws WorkflowExecutionException {
        try {
            return stub.getResult(String.class);
        } catch (WorkflowFailedException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new WorkflowExecutionException(msg != null ? msg : "Workflow failed", e);
        } catch (Exception e) {
            throw new WorkflowExecutionException(e);
        }
    }

    /**
     * Awaits the workflow result on {@code executor} and invokes exactly one of the callbacks.
     */
    public void awaitResultAsync(
            Executor executor,
            Consumer<String> onSuccess,
            Consumer<WorkflowExecutionException> onFailure) {
        executor.execute(() -> {
            try {
                onSuccess.accept(awaitResult());
            } catch (WorkflowExecutionException e) {
                onFailure.accept(e);
            }
        });
    }
}
