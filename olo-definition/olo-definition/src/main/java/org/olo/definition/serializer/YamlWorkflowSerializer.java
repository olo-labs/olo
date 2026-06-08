package org.olo.definition.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.definition.workflow.WorkflowDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * YAML serialization for {@link WorkflowDefinition}.
 */
public final class YamlWorkflowSerializer implements WorkflowSerializer {

    private final ObjectMapper mapper;
    private final Charset charset;

    public YamlWorkflowSerializer() {
        this(JacksonWorkflowMapper.yamlMapper());
    }

    public YamlWorkflowSerializer(ObjectMapper mapper) {
        this(mapper, StandardCharsets.UTF_8);
    }

    public YamlWorkflowSerializer(ObjectMapper mapper, Charset charset) {
        this.mapper = mapper;
        this.charset = charset;
    }

    @Override
    public String serialize(WorkflowDefinition workflow) throws IOException {
        return mapper.writeValueAsString(workflow);
    }

    @Override
    public byte[] serializeToBytes(WorkflowDefinition workflow) throws IOException {
        return serialize(workflow).getBytes(charset);
    }

    @Override
    public void serialize(WorkflowDefinition workflow, OutputStream output) throws IOException {
        output.write(serializeToBytes(workflow));
    }

    @Override
    public WorkflowDefinition deserialize(String content) throws IOException {
        return mapper.readValue(content, WorkflowDefinition.class);
    }

    @Override
    public WorkflowDefinition deserialize(byte[] content) throws IOException {
        return mapper.readValue(new String(content, charset), WorkflowDefinition.class);
    }

    @Override
    public WorkflowDefinition deserialize(InputStream input) throws IOException {
        return mapper.readValue(input, WorkflowDefinition.class);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }
}
