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

        WorkflowClientOptions.Builder clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(builder.namespace);
        if (builder.dataConverter != null) {
            clientOptions.setDataConverter(builder.dataConverter);
        }

        this.workflowClient = WorkflowClient.newInstance(serviceStubs, clientOptions.build());
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Starts the configured chat workflow type on {@code taskQueue} as {@code workflowId}. The caller names the workflow so
     * the id can carry the run's tenant, which the payload codec seals with (OLO M5.2).
     */
    public ChatWorkflowHandle startChatWorkflow(String workflowId, String taskQueue, Object workflowInput) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        if (taskQueue == null || taskQueue.isBlank()) {
            throw new IllegalArgumentException("taskQueue is required");
        }
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue.trim())
                .setWorkflowExecutionTimeout(java.time.Duration.ofDays(30))
                .build();
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowType, options);
        stub.start(workflowInput);
        return new ChatWorkflowHandle(stub);
    }

    /**
     * Signals human input on the chat workflow {@code workflowId}.
     */
    public void signalHumanInput(String workflowId, boolean approved, String message) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
        stub.signal(HUMAN_INPUT_SIGNAL, approved, message != null ? message : "");
    }

    /**
     * Requests cancellation of the chat workflow {@code workflowId}.
     */
    public void cancelChatWorkflow(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId);
        stub.cancel();
    }

    public void close() {
        serviceStubs.shutdown();
    }

    public static final class Builder {
        private String target = "localhost:7233";
        private String namespace = "default";
        private String workflowType;
        private io.temporal.common.converter.DataConverter dataConverter;

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

        /** Converter for every payload this client writes and reads; OLO uses its payload codec (M5.2). */
        public Builder dataConverter(io.temporal.common.converter.DataConverter dataConverter) {
            this.dataConverter = dataConverter;
            return this;
        }

        public TemporalClient build() {
            return new TemporalClient(this);
        }
    }
}
