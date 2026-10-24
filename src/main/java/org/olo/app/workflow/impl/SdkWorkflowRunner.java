/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.workflow.impl;

import org.olo.app.workflow.WorkflowRunCompletion;
import org.olo.app.workflow.WorkflowRunner;
import org.olo.input.model.WorkflowInput;
import org.olo.input.codec.TenantWorkflowIds;
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
    private final org.olo.app.store.ChatRunStore runStore;

    public SdkWorkflowRunner(
            TemporalClient temporalClient,
            @Qualifier("oloTaskQueue") String defaultTaskQueue,
            @Qualifier("workflowCompletionExecutor") Executor workflowCompletionExecutor,
            org.olo.app.store.ChatRunStore runStore) {
        this.temporalClient = temporalClient;
        this.runStore = runStore;
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
        String tenantId = workflowInput.getContext() != null ? workflowInput.getContext().getTenantId() : null;
        ChatWorkflowHandle handle = temporalClient.startChatWorkflow(
                TenantWorkflowIds.run(tenantId, runId), effectiveTaskQueue, workflowInput);
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
        temporalClient.signalHumanInput(workflowId(runId), approved, message);
    }

    @Override
    public void cancelChatRun(String runId) {
        temporalClient.cancelChatWorkflow(workflowId(runId));
    }

    /** The run's workflow id carries its tenant, which the payload codec seals with (M5.2). */
    private String workflowId(String runId) {
        org.olo.app.store.ChatRunStore.RunRecord run = runStore.get(runId);
        if (run == null || run.tenantId == null) {
            throw new IllegalArgumentException("unknown run " + runId);
        }
        return TenantWorkflowIds.run(run.tenantId, runId);
    }
}
