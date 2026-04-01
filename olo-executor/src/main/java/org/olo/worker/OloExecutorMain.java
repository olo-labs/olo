/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.worker;

import org.olo.worker.activities.impl.OloChatActivitiesImpl;
import org.olo.worker.workflow.impl.OloChatWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.WorkerFactory;

/**
 * Starts the Olo executor: the separate process that runs Temporal workflows and activities.
 * Task queue: olo-chat (must match Chat BE config). Connects to local Temporal server (localhost:7233) by default.
 * This process is not part of the backend; it executes workflow logic and callbacks to Chat BE.
 */
public class OloExecutorMain {

    private static final String TASK_QUEUE = System.getenv().getOrDefault("OLO_TASK_QUEUE", "olo-chat");
    private static final String NAMESPACE = System.getenv().getOrDefault("OLO_TEMPORAL_NAMESPACE", "default");

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();

        WorkflowClient client = WorkflowClient.newInstance(service,
                WorkflowClientOptions.newBuilder().setNamespace(NAMESPACE).build());

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE, WorkerOptions.newBuilder().build());

        worker.registerWorkflowImplementationTypes(OloChatWorkflowImpl.class);
        worker.registerActivitiesImplementations(new OloChatActivitiesImpl());

        factory.start();
        System.out.println("Olo executor started. Task queue: " + TASK_QUEUE + ", namespace: " + NAMESPACE);
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
