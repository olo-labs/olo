package org.olo.definition.runtime;

import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.spi.runtime.RuntimeCapability;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRuntimeDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void materializesInlineRuntimeBaselineWhenUnset() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Planner")
                .id("planner")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        assertThat(workflow.getRuntime()).isNotNull();
        assertThat(workflow.getRuntime().getContractVersion())
                .isEqualTo(WorkflowRuntimeDefinition.DEFAULT_CONTRACT_VERSION);
        assertThat(workflow.getRuntime().getExecutionModel()).isEqualTo(ExecutionModel.INLINE);
        assertThat(workflow.getRuntime().getCapabilities()).isEmpty();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"runtime\"");
        assertThat(serialized).contains("\"contractVersion\"");
        assertThat(serialized).contains("\"1.0\"");
        assertThat(serialized).contains("INLINE");
        assertThat(serialized).doesNotContain("\"capabilities\"");
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void roundTripsWorkflowRuntimeCapabilities() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Agent")
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .agentWorkflowRuntime()
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        assertThat(workflow.getRuntime()).isNotNull();
        assertThat(workflow.getRuntime().getContractVersion()).isEqualTo(WorkflowRuntimeDefinition.DEFAULT_CONTRACT_VERSION);
        assertThat(workflow.getRuntime().getExecutionModel()).isEqualTo(ExecutionModel.CHILD_WORKFLOW);
        assertThat(workflow.getRuntime().isDebuggable()).isTrue();
        assertThat(workflow.getRuntime().isReplayable()).isTrue();
        assertThat(workflow.getRuntime().isTimeoutAware()).isTrue();
        assertThat(workflow.getRuntime().getDefaultTimeout()).isEqualTo("PT10M");
        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"contractVersion\"");
        assertThat(serialized).contains("\"executionModel\"");
        assertThat(serialized).contains("CHILD_WORKFLOW");
        assertThat(serialized).contains("TIMEOUT");
        assertThat(serialized).contains("\"defaultTimeout\"");
        assertThat(serialized).contains("PT10M");
        assertThat(serialized).contains("\"capabilities\"");
        assertThat(serialized).contains("DEBUG");
        assertThat(serialized).contains("REPLAY");
        assertThat(serialized).doesNotContain("\"debuggable\"");

        WorkflowDefinition restored = json.deserialize(serialized);
        assertThat(restored.getRuntime().getContractVersion()).isEqualTo("1.0");
        assertThat(restored.getRuntime().getExecutionModel()).isEqualTo(ExecutionModel.CHILD_WORKFLOW);
        assertThat(restored.getRuntime().getCapabilities())
                .containsExactly(
                        RuntimeCapability.DEBUG,
                        RuntimeCapability.REPLAY,
                        RuntimeCapability.TIMEOUT);
        assertThat(restored.getRuntime().getDefaultTimeout()).isEqualTo("PT10M");
    }
}
