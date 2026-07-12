/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.workflow.impl;

import org.olo.app.workflow.WorkflowRunCompletion;
import org.olo.app.workflow.WorkflowRunner;
import org.olo.input.model.WorkflowInput;
import org.olo.temporal.sdk.ChatWorkflowHandle;
import org.olo.temporal.sdk.TemporalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

/**
 * {@link WorkflowRunner} backed by {@link TemporalClient} (olo-temporal-sdk).
 */
@Service
public class SdkWorkflowRunner implements WorkflowRunner {

    private static final Logger log = LoggerFactory.getLogger(SdkWorkflowRunner.class);

    private final TemporalClient temporalClient;
    private final String defaultTaskQueue;
    private final Executor workflowCompletionExecutor;

    public SdkWorkflowRunner(
            TemporalClient temporalClient,
            @Qualifier("oloTaskQueue") String defaultTaskQueue,
            @Qualifier("workflowCompletionExecutor") Executor workflowCompletionExecutor) {
        this.temporalClient = temporalClient;
        this.defaultTaskQueue = defaultTaskQueue;
        this.workflowCompletionExecutor = workflowCompletionExecutor;
    }

    @Override
    public void startChatRun(
            String runId,
            WorkflowInput workflowInput,
            String taskQueue,
            WorkflowRunCompletion completion) {
        String effectiveTaskQueue = (taskQueue != null && !taskQueue.isBlank())
                ? taskQueue.trim()
                : defaultTaskQueue;
        log.info("Starting chat workflow runId={} taskQueue={}", runId, effectiveTaskQueue);
        ChatWorkflowHandle handle = temporalClient.startChatWorkflow(runId, effectiveTaskQueue, workflowInput);
        handle.awaitResultAsync(
                workflowCompletionExecutor,
                result -> {
                    log.info("Chat workflow completed runId={} hasResult={}", runId, result != null && !result.isBlank());
                    completion.onCompleted(result);
                },
                ex -> {
                    log.warn("Chat workflow failed runId={}: {}", runId, ex.getMessage());
                    String msg = ex.getMessage() != null ? ex.getMessage() : "Workflow failed";
                    completion.onFailed(msg);
                });
    }

    @Override
    public void signalHumanInput(String runId, boolean approved, String message) {
        temporalClient.signalHumanInput(runId, approved, message);
    }

    @Override
    public void cancelChatRun(String runId) {
        temporalClient.cancelChatWorkflow(runId);
    }
}
