package org.olo.definition.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.olo.definition.parameter.WorkflowParameterDefinition;
import org.olo.definition.parameter.WorkflowParameterDefinitionDeserializer;
import org.olo.definition.workflow.ChildWorkflowDefinition;
import org.olo.definition.workflow.ChildWorkflowDefinitionDeserializer;

/**
 * Shared Jackson configuration for workflow JSON and YAML.
 */
public final class JacksonWorkflowMapper {

    private JacksonWorkflowMapper() {
    }

    public static ObjectMapper jsonMapper() {
        return configure(new ObjectMapper());
    }

    public static ObjectMapper yamlMapper() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        return configure(new ObjectMapper(factory));
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        SimpleModule workflowModule = new SimpleModule();
        workflowModule.addDeserializer(ChildWorkflowDefinition.class, new ChildWorkflowDefinitionDeserializer());
        workflowModule.addDeserializer(
                WorkflowParameterDefinition.class, new WorkflowParameterDefinitionDeserializer());
        mapper.registerModule(workflowModule);
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        return mapper;
    }
}
