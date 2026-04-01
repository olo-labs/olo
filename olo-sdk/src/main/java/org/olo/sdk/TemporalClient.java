/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.sdk;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public final class TemporalClient {

    private final String workflowType;
    private final WorkflowServiceStubs serviceStubs;
    private final WorkflowClient workflowClient;

    private TemporalClient(Builder builder) {
        this.workflowType = builder.workflowType != null && !builder.workflowType.isEmpty()
                ? builder.workflowType
                : "OloKernelWorkflow";

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

    public WorkflowClient getWorkflowClient() {
        return workflowClient;
    }

    /**
     * Creates an untyped workflow stub for the Olo chat workflow with the given options.
     * Encapsulates the workflow type name so the backend does not depend on it;
     * renaming the workflow only requires changing the SDK.
     */
    public WorkflowStub newChatWorkflowStub(WorkflowOptions options) {
        return workflowClient.newUntypedWorkflowStub(workflowType, options);
    }

    public void close() {
        serviceStubs.shutdown();
    }

    public static final class Builder {
        private String target = "localhost:7233";
        private String namespace = "default";
        private String workflowType;  // e.g. from env OLO_WORKFLOW_TYPE (OloKernelWorkflow)

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /** Workflow type name (e.g. OloKernelWorkflow). Set from env OLO_WORKFLOW_TYPE in the backend. */
        public Builder workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }

        public TemporalClient build() {
            return new TemporalClient(this);
        }
    }
}
