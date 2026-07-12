/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Deserializes WorkflowInput from either a JSON object or a JSON string (string that contains JSON).
 * When Temporal sends the workflow argument as a string payload, the converter receives a string;
 * this deserializer parses it so WorkflowInput can be used as the workflow parameter type.
 */
public final class WorkflowInputDeserializer extends JsonDeserializer<WorkflowInput> {

    private static final ObjectMapper MAPPER = WorkflowInputMapper.jsonMapper();

    @Override
    public WorkflowInput deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == null) {
            p.nextToken();
        }
        switch (p.getCurrentToken()) {
            case VALUE_STRING:
                String json = p.getText();
                if (json == null || json.isBlank()) return null;
                return parseFromString(json.trim());
            case START_OBJECT:
                return MAPPER.readValue(p, WorkflowInput.class);
            default:
                return ctxt.reportInputMismatch(WorkflowInput.class,
                        "Expected JSON object or string, got %s", p.getCurrentToken());
        }
    }

    /** Parse JSON string to WorkflowInput without recursion (build from tree). */
    private static WorkflowInput parseFromString(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        String version = root.has("version") ? root.get("version").asText(null) : null;
        List<InputItem> inputs = root.has("inputs") && root.get("inputs").isArray()
                ? MAPPER.convertValue(root.get("inputs"), MAPPER.getTypeFactory().constructCollectionType(List.class, InputItem.class))
                : List.of();
        Context context = root.has("context") ? MAPPER.treeToValue(root.get("context"), Context.class) : null;
        Routing routing = root.has("routing") ? MAPPER.treeToValue(root.get("routing"), Routing.class) : null;
        Metadata metadata = root.has("metadata") ? MAPPER.treeToValue(root.get("metadata"), Metadata.class) : null;
        Execution execution = root.has("execution") ? MAPPER.treeToValue(root.get("execution"), Execution.class) : null;
        return new WorkflowInput(version, inputs, context, routing, metadata, execution);
    }
}
