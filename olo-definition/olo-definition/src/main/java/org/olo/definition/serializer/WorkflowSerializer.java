/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.serializer;

import org.olo.definition.workflow.WorkflowDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Serializes and deserializes {@link WorkflowDefinition} instances.
 */
public interface WorkflowSerializer {

    Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    String serialize(WorkflowDefinition workflow) throws IOException;

    byte[] serializeToBytes(WorkflowDefinition workflow) throws IOException;

    void serialize(WorkflowDefinition workflow, OutputStream output) throws IOException;

    WorkflowDefinition deserialize(String content) throws IOException;

    WorkflowDefinition deserialize(byte[] content) throws IOException;

    WorkflowDefinition deserialize(InputStream input) throws IOException;
}
