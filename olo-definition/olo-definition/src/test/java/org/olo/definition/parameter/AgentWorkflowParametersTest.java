package org.olo.definition.parameter;

import org.olo.definition.runtime.AgentDelegationPolicy;
import org.olo.definition.planner.AgentAvailableAgents;
import org.olo.definition.planner.WorkflowPlannerMetadata;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWorkflowParametersTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void builderAppliesAgentParameterDefaults() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent")
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .agentParameters()
                .build();

        assertThat(workflow.getParameters()).containsOnlyKeys(
                AgentWorkflowParameters.MAX_ITERATIONS,
                AgentWorkflowParameters.SYSTEM_PROMPT,
                AgentWorkflowParameters.MODEL,
                AgentWorkflowParameters.TEMPERATURE);
        assertThat(workflow.getParameters().get(AgentWorkflowParameters.MAX_ITERATIONS).getDefaultValue())
                .isEqualTo(10);
        assertThat(workflow.getParameters().get(AgentWorkflowParameters.TEMPERATURE).getDefaultValue())
                .isEqualTo(0.2);
        assertThat(workflow.getParameters().get(AgentWorkflowParameters.SYSTEM_PROMPT).getDefaultValue())
                .isEqualTo("");

        WorkflowParameterDefinition temperature =
                workflow.getParameters().get(AgentWorkflowParameters.TEMPERATURE);
        assertThat(temperature.getType()).isEqualTo("number");
        assertThat(temperature.getLabel()).isEqualTo("Temperature");
        assertThat(temperature.getMinimum()).isEqualTo(0d);
        assertThat(temperature.getMaximum()).isEqualTo(2d);
        assertThat(temperature.getStep()).isEqualTo(0.1d);
        assertThat(temperature.getUi().getWidget()).isEqualTo("SLIDER");
    }

    @Test
    void normalizesLegacyWidgetNamesOnDeserialize() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test",
                    "tags": [],
                    "examples": [],
                    "required_inputs": [],
                    "required_outputs": [],
                    "tool_requirements": [],
                    "required_context": []
                  },
                  "parameters": {
                    "temperature": {
                      "type": "number",
                      "ui": { "widget": "slider" }
                    }
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        assertThat(workflow.getParameters().get("temperature").getUi().getWidget()).isEqualTo("SLIDER");
    }

    @Test
    void deserializesShorthandParameterLiterals() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test",
                    "tags": [],
                    "examples": [],
                    "required_inputs": [],
                    "required_outputs": [],
                    "tool_requirements": [],
                    "required_context": []
                  },
                  "parameters": {
                    "maxIterations": 10,
                    "systemPrompt": "",
                    "model": "",
                    "temperature": 0.2
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        assertThat(workflow.getParameters().get("maxIterations").getType()).isEqualTo("integer");
        assertThat(workflow.getParameters().get("maxIterations").getDefaultValue()).isEqualTo(10);
        assertThat(workflow.getParameters().get("systemPrompt").getType()).isEqualTo("string");
        assertThat(workflow.getParameters().get("temperature").getDefaultValue()).isEqualTo(0.2);
    }

    @Test
    void agentPresetCarriesAgentParameters() throws Exception {
        Path preset = Path.of("olo-configuration/default/agent.json").toAbsolutePath().normalize();
        if (!preset.toFile().exists()) {
            preset = Path.of("../olo-configuration/default/agent.json").toAbsolutePath().normalize();
        }
        if (!preset.toFile().exists()) {
            throw new org.opentest4j.TestAbortedException("agent preset not found");
        }

        WorkflowDefinition workflow = json.deserialize(Files.readString(preset));
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
        assertThat(workflow.getParameters().keySet()).containsExactlyInAnyOrder(
                AgentWorkflowParameters.MAX_ITERATIONS,
                AgentWorkflowParameters.SYSTEM_PROMPT,
                AgentWorkflowParameters.MODEL,
                AgentWorkflowParameters.TEMPERATURE);
        assertThat(workflow.getMetadata().get(WorkflowPlannerMetadata.ROLE))
                .isEqualTo(WorkflowPlannerMetadata.ROLE_AGENT);
        assertThat(workflow.getMetadata().get(WorkflowPlannerMetadata.AGENT_TYPE))
                .isEqualTo(WorkflowPlannerMetadata.AGENT_TYPE_AUTONOMOUS);
        assertThat(workflow.getMetadata().get(WorkflowPlannerMetadata.PLANNING_STRATEGY))
                .isEqualTo(WorkflowPlannerMetadata.PLANNING_STRATEGY_REACT);
        assertThat(workflow.getMetadata().get(WorkflowPlannerMetadata.AGENT_SELECTION_STRATEGY))
                .isEqualTo(WorkflowPlannerMetadata.AGENT_SELECTION_STRATEGY_DYNAMIC);
        assertThat(workflow.getChildWorkflows()).hasSize(4);
        assertThat(workflow.getAvailableAgents()).isEqualTo(AgentAvailableAgents.agentPresetDefaults());
        assertThat(workflow.getRuntime().getDelegation()).isEqualTo(AgentDelegationPolicy.agentPresetDefaults());
        assertThat(workflow.getTools()).isEmpty();
    }

    @Test
    void deserializesChildWorkflowIdShorthand() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "capability": {
                    "name": "Agent",
                    "description": "test",
                    "tags": [],
                    "examples": [],
                    "required_inputs": [],
                    "required_outputs": [],
                    "tool_requirements": [],
                    "required_context": []
                  },
                  "childWorkflows": ["planner", "research-agent"]
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        assertThat(workflow.getChildWorkflows()).hasSize(2);
        assertThat(workflow.getChildWorkflows().get(0).getWorkflowId()).isEqualTo("planner");
        assertThat(workflow.getChildWorkflows().get(1).getWorkflowId()).isEqualTo("research-agent");
    }
}
