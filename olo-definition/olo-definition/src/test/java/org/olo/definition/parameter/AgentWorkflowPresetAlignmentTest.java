package org.olo.definition.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.olo.annotation.OloCatalogLocations;

import java.io.InputStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures {@code agent.json} parameters stay aligned with {@code @OloWorkflowPreset} catalog output.
 */
class AgentWorkflowPresetAlignmentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void agentParametersMatchWorkflowPresetCatalog() throws Exception {
        try (InputStream in =
                AgentWorkflowPresetAlignmentTest.class
                        .getClassLoader()
                        .getResourceAsStream(OloCatalogLocations.WORKFLOW_PRESETS_CATALOG)) {
            assertThat(in)
                    .as("olo-core-nodes must be on the test classpath (%s)", OloCatalogLocations.WORKFLOW_PRESETS_CATALOG)
                    .isNotNull();

            JsonNode agentPreset = StreamSupport.stream(
                            MAPPER.readTree(in).get("presets").spliterator(), false)
                    .filter(node -> "agent".equals(node.path("id").asText(null)))
                    .findFirst()
                    .orElseThrow();
            JsonNode catalogParameters = agentPreset.get("parameters");
            assertThat(catalogParameters.isArray()).isTrue();

            var workflowParameters = AgentWorkflowParameters.defaults();
            assertThat(workflowParameters.keySet())
                    .containsExactlyInAnyOrder("systemPrompt", "maxIterations", "model", "temperature");

            for (var entry : workflowParameters.entrySet()) {
                JsonNode catalogParameter = findParameter(catalogParameters, entry.getKey());
                WorkflowParameterDefinition workflowParameter = entry.getValue();
                assertThat(workflowParameter.getLabel()).isEqualTo(catalogParameter.get("label").asText());
                assertThat(workflowParameter.getType()).isEqualTo(catalogParameter.get("type").asText());
                assertThat(workflowParameter.getRequired()).isEqualTo(catalogParameter.get("required").asBoolean());
                if (catalogParameter.has("defaultValue") && !catalogParameter.get("defaultValue").isNull()) {
                    JsonNode expected = catalogParameter.get("defaultValue");
                    Object actual = workflowParameter.getDefaultValue();
                    if (expected.isNumber()) {
                        assertThat(((Number) actual).doubleValue()).isEqualTo(expected.asDouble());
                    } else {
                        assertThat(actual).isEqualTo(expected.isTextual() ? expected.asText() : expected);
                    }
                }
            }
        }
    }

    private static JsonNode findParameter(JsonNode parameters, String id) {
        return StreamSupport.stream(parameters.spliterator(), false)
                .filter(node -> id.equals(node.path("id").asText(null)))
                .findFirst()
                .orElseThrow();
    }
}
