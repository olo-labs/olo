/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.workflow.impl;

import org.olo.input.model.Context;
import org.olo.input.model.InputItem;
import org.olo.input.model.InputType;
import org.olo.input.model.Metadata;
import org.olo.input.model.Routing;
import org.olo.input.model.Storage;
import org.olo.input.model.StorageMode;
import org.olo.input.model.TransactionType;
import org.olo.input.model.WorkflowInput;

import java.util.Collections;
import java.util.List;

/**
 * Builds {@link WorkflowInput} (olo-worker-input format) for the chat workflow.
 * Use this before starting the workflow so the executor receives the standard input format.
 */
public final class WorkflowInputSerializer {

    private static final String VERSION = "1.0";
    private static final String USER_QUERY_INPUT_NAME = "userQuery";
    private static final String USER_QUERY_DISPLAY_NAME = "User query";

    /**
     * Builds and returns the WorkflowInput object.
     * Pass this to the workflow so Temporal serializes it as a JSON object, not as a string.
     * correlationId is set at run creation for cross-service tracing and propagated to every event.
     */
    public static WorkflowInput build(String tenantId,
                                     String sessionId,
                                     String messageId,
                                     String userMessage,
                                     String pipeline,
                                     String transactionId,
                                     String runId,
                                     String callbackBaseUrl,
                                     String correlationId) {
        String userMessageSafe = userMessage != null ? userMessage : "";

        InputItem userQueryInput = new InputItem(
                USER_QUERY_INPUT_NAME,
                USER_QUERY_DISPLAY_NAME,
                InputType.STRING,
                new Storage(StorageMode.LOCAL, null, null),
                userMessageSafe
        );

        Context context = new Context(
                tenantId != null ? tenantId : "",
                "",
                List.of("PUBLIC"),
                Collections.emptyList(),
                sessionId != null ? sessionId : "",
                runId != null ? runId : "",
                callbackBaseUrl != null ? callbackBaseUrl : "",
                correlationId != null ? correlationId : ""
        );

        Routing routing = new Routing(
                pipeline != null ? pipeline : "olo-chat",
                TransactionType.QUESTION_ANSWER,
                transactionId != null ? transactionId : ""
        );

        Metadata metadata = new Metadata(null, 0L);

        return WorkflowInput.builder()
                .version(VERSION)
                .addInput(userQueryInput)
                .context(context)
                .routing(routing)
                .metadata(metadata)
                .build();
    }
}
