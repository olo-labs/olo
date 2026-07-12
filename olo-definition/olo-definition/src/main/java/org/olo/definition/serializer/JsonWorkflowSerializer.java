/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.definition.workflow.WorkflowDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * JSON serialization for {@link WorkflowDefinition}.
 */
public final class JsonWorkflowSerializer implements WorkflowSerializer {

    private final ObjectMapper mapper;
    private final Charset charset;

    public JsonWorkflowSerializer() {
        this(JacksonWorkflowMapper.jsonMapper());
    }

    public JsonWorkflowSerializer(ObjectMapper mapper) {
        this(mapper, DEFAULT_CHARSET);
    }

    public JsonWorkflowSerializer(ObjectMapper mapper, Charset charset) {
        this.mapper = mapper;
        this.charset = charset;
    }

    @Override
    public String serialize(WorkflowDefinition workflow) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(workflow);
    }

    @Override
    public byte[] serializeToBytes(WorkflowDefinition workflow) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(workflow);
    }

    @Override
    public void serialize(WorkflowDefinition workflow, OutputStream output) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, workflow);
    }

    @Override
    public WorkflowDefinition deserialize(String content) throws IOException {
        return mapper.readValue(content, WorkflowDefinition.class);
    }

    @Override
    public WorkflowDefinition deserialize(byte[] content) throws IOException {
        return mapper.readValue(content, WorkflowDefinition.class);
    }

    @Override
    public WorkflowDefinition deserialize(InputStream input) throws IOException {
        return mapper.readValue(input, WorkflowDefinition.class);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public Charset getCharset() {
        return charset;
    }
}
