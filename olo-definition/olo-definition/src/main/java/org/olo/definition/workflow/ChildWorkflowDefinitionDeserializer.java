/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.workflow;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Accepts workflow-id shorthand ({@code "planner"}) or full objects ({@code workflowId}, {@code workflowVersion}).
 */
public final class ChildWorkflowDefinitionDeserializer extends JsonDeserializer<ChildWorkflowDefinition> {

    @Override
    public ChildWorkflowDefinition deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("child workflow entry is required");
        }
        if (node.isTextual()) {
            return ChildWorkflowDefinition.builder().workflowId(node.textValue()).build();
        }
        if (node.isObject()) {
            return fromObject(node);
        }
        throw new IllegalArgumentException("unsupported child workflow entry: " + node);
    }

    private static ChildWorkflowDefinition fromObject(JsonNode node) {
        JsonNode workflowIdNode = node.get("workflowId");
        if (workflowIdNode == null || workflowIdNode.isNull()) {
            workflowIdNode = node.get("queue");
        }
        if (workflowIdNode == null || workflowIdNode.isNull()) {
            throw new IllegalArgumentException("child workflow workflowId is required");
        }
        ChildWorkflowDefinition.Builder builder =
                ChildWorkflowDefinition.builder().workflowId(workflowIdNode.asText());
        JsonNode versionNode = node.get("workflowVersion");
        if (versionNode != null && !versionNode.isNull()) {
            builder.workflowVersion(versionNode.asText());
        }
        return builder.build();
    }
}
