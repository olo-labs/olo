package org.olo.definition.state;

import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.parameter.WorkflowParameterDefinition;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStateTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsStateInputsParametersAndNodeAccess() throws Exception {
        WorkflowDefinition withWorkflowRef = WorkflowDefinition.builder()
                .id("multi-agent-state")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").required(true).build())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .putParameter("temperature", WorkflowParameterDefinition.builder()
                        .schema("number")
                        .defaultValue(0.2)
                        .build())
                .addNode(ValidationTestFixtures.node("research", NodeType.TOOL)
                        .addRead("state.symbol")
                        .addWrite("state.analysis")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(withWorkflowRef).valid()).isTrue();

        String serialized = json.serialize(withWorkflowRef);
        assertThat(serialized).contains("\"state\"");
        assertThat(serialized).contains("\"reads\"");
        assertThat(serialized).contains("state.symbol");

        WorkflowDefinition restored = json.deserialize(serialized);
        assertThat(restored).isEqualTo(withWorkflowRef);
        assertThat(restored.getParameters().get("temperature").getDefaultValue()).isEqualTo(0.2);
    }

    @Test
    void rejectsUnknownStateFieldReference() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("bad-state")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("symbol", StateFieldDefinition.builder().schema("String").build())
                .addNode(ValidationTestFixtures.node("n1", NodeType.TOOL)
                        .addRead("state.missing")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("reads unknown state field"));
    }

    @Test
    void inputAutoPopulatesStateForValidation() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("auto-populate")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").required(true).build())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .putState("news", StateFieldDefinition.builder().schema("News[]").build())
                .addNode(ValidationTestFixtures.node("research", NodeType.TOOL)
                        .addRead("state.symbol")
                        .addWrite("state.analysis")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
        assertThat(EffectiveStateFields.names(workflow)).containsExactlyInAnyOrder("symbol", "analysis", "news");
    }

    @Test
    void rejectsRedundantInputAndStateDeclaration() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("redundant")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").build())
                .putState("symbol", StateFieldDefinition.builder().schema("String").build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("auto-populates state"));
    }

    @Test
    void populateStateFalseRequiresExplicitStateField() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("no-auto")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder()
                        .schema("String")
                        .populateState(false)
                        .build())
                .addNode(ValidationTestFixtures.node("n1", NodeType.TOOL)
                        .addRead("state.symbol")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
    }

    @Test
    void rejectsTypoInWritePath() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("typo-write")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .addNode(ValidationTestFixtures.node("research", NodeType.TOOL)
                        .addWrite("state.analysys")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("writes unknown state field: state.analysys"));
    }
}
