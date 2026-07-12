/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.temporal.sdk;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Olo Temporal SDK entry point. All Temporal communication from the backend should go through this class.
 */
public final class TemporalClient {

    static final String HUMAN_INPUT_SIGNAL = "humanInput";
    static final String RUN_WORKFLOW_ID_PREFIX = "run-";

    private final String workflowType;
    private final WorkflowServiceStubs serviceStubs;
    private final WorkflowClient workflowClient;

    private TemporalClient(Builder builder) {
        this.workflowType = builder.workflowType != null && !builder.workflowType.isEmpty()
                ? builder.workflowType
                : "olo";

        WorkflowServiceStubsOptions stubsOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(builder.target)
                .build();

        this.serviceStubs = WorkflowServiceStubs.newServiceStubs(stubsOptions);

        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(builder.namespace)
                .build();

        this.workflowClient = WorkflowClient.newInstance(serviceStubs, clientOptions);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Starts the configured chat workflow type on {@code taskQueue} with workflow id {@code run-{runId}}.
     */
    public ChatWorkflowHandle startChatWorkflow(String runId, String taskQueue, Object workflowInput) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        if (taskQueue == null || taskQueue.isBlank()) {
            throw new IllegalArgumentException("taskQueue is required");
        }
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(runWorkflowId(runId))
                .setTaskQueue(taskQueue.trim())
                .build();
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowType, options);
        stub.start(workflowInput);
        return new ChatWorkflowHandle(stub);
    }

    /**
     * Signals human input on the chat workflow for {@code runId}.
     */
    public void signalHumanInput(String runId, boolean approved, String message) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runWorkflowId(runId));
        stub.signal(HUMAN_INPUT_SIGNAL, approved, message != null ? message : "");
    }

    /**
     * Requests cancellation of the chat workflow for {@code runId}.
     */
    public void cancelChatWorkflow(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId is required");
        }
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(runWorkflowId(runId));
        stub.cancel();
    }

    public static String runWorkflowId(String runId) {
        return RUN_WORKFLOW_ID_PREFIX + runId.trim();
    }

    public void close() {
        serviceStubs.shutdown();
    }

    public static final class Builder {
        private String target = "localhost:7233";
        private String namespace = "default";
        private String workflowType;

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /** Workflow type name (e.g. olo). Set from workflow.json in the backend. */
        public Builder workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }

        public TemporalClient build() {
            return new TemporalClient(this);
        }
    }
}
