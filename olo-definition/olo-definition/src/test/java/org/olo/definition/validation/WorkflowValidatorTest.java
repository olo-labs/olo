package org.olo.definition.validation;

import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.edge.EdgeDefinition;
import org.olo.definition.execution.ExecutionKind;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.error.ErrorRoute;
import org.olo.definition.error.OnFailureDefinition;
import org.olo.definition.error.RetryPolicy;
import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.port.PortDefinition;
import org.olo.definition.port.PortDirection;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.parameter.WorkflowParameterDefinition;
import org.olo.definition.state.StateFieldDefinition;
import org.olo.definition.runtime.WorkflowRuntimeDefinition;
import org.olo.spi.runtime.RuntimeCapability;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowValidatorTest {

    @Test
    void acceptsValidWorkflow() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Valid")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("in")
                .outputNode("out")
                .connect("in", "out")
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
        WorkflowValidator.validateOrThrow(workflow);
    }

    @Test
    void rejectsUnknownEdgeEndpoints() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("broken")
                .capability(ValidationTestFixtures.minimalCapability())
                .addEdge(EdgeDefinition.builder().sourceNodeId("a").targetNodeId("b").build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown source node"));
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown target node"));
    }

    @Test
    void acceptsCompatibleTypedPorts() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("typed-ok")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("tool-a", NodeType.TOOL)
                        .addPort(PortDefinition.outputPort("stockList", "Stock[]"))
                        .build())
                .addNode(ValidationTestFixtures.node("tool-b", NodeType.TOOL)
                        .addPort(PortDefinition.inputPort("stocks", "Stock[]"))
                        .build())
                .addEdge(EdgeDefinition.builder()
                        .sourceNodeId("tool-a")
                        .sourcePortId("stockList")
                        .targetNodeId("tool-b")
                        .targetPortId("stocks")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void rejectsIncompatibleTypedPorts() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("typed-bad")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("tool-a", NodeType.TOOL)
                        .addPort(PortDefinition.outputPort("result", "String"))
                        .build())
                .addNode(ValidationTestFixtures.node("tool-b", NodeType.TOOL)
                        .addPort(PortDefinition.inputPort("stocks", "Stock[]"))
                        .build())
                .addEdge(EdgeDefinition.builder()
                        .sourceNodeId("tool-a")
                        .sourcePortId("result")
                        .targetNodeId("tool-b")
                        .targetPortId("stocks")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("schema mismatch"));
        assertThat(result.errors()).anyMatch(e -> e.contains("String"));
        assertThat(result.errors()).anyMatch(e -> e.contains("Stock[]"));
    }

    @Test
    void rejectsUnknownOutputPort() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("unknown-port")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("tool-a", NodeType.TOOL)
                        .addPort(PortDefinition.outputPort("out", "String"))
                        .build())
                .addNode(ValidationTestFixtures.node("tool-b", NodeType.TOOL).build())
                .addEdge(EdgeDefinition.builder()
                        .sourceNodeId("tool-a")
                        .sourcePortId("missing")
                        .targetNodeId("tool-b")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown output port"));
    }

    @Test
    void acceptsHumanNodeWithApproval() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("hitl-ok")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("trade-approval", NodeType.HUMAN)
                        .subtype("APPROVAL")
                        .approval(HumanApprovalDefinition.builder()
                                .title("Approve trade?")
                                .approvers(List.of("trading-desk"))
                                .build())
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void rejectsHumanNodeWithoutApproval() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("hitl-bad")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("trade-approval", NodeType.HUMAN).build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("requires an approval block"));
    }

    @Test
    void acceptsOnFailureWithRetryAndRoute() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("failure-ok")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("openai", NodeType.MODEL)
                        .onFailure(OnFailureDefinition.builder()
                                .retry(RetryPolicy.builder().attempts(3).build())
                                .route(ErrorRoute.builder().targetNodeId("fallback-model").build())
                                .build())
                        .build())
                .addNode(ValidationTestFixtures.node("fallback-model", NodeType.MODEL).build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void rejectsOnFailureRouteToUnknownNode() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("failure-bad")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("openai", NodeType.MODEL)
                        .onFailure(OnFailureDefinition.builder()
                                .route(ErrorRoute.builder().targetNodeId("missing").build())
                                .build())
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown target node"));
    }

    @Test
    void rejectsOnFailureWithZeroAttempts() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("failure-retry")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("openai", NodeType.MODEL)
                        .onFailure(OnFailureDefinition.builder()
                                .retry(RetryPolicy.builder().attempts(0).build())
                                .build())
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("attempts must be >= 1"));
    }

    @Test
    void requiresPortNameWhenMultipleOutputsDeclared() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("multi-out")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("router", NodeType.CONDITION)
                        .addPort(PortDefinition.outputPort("true", "any"))
                        .addPort(PortDefinition.outputPort("false", "any"))
                        .build())
                .addNode(ValidationTestFixtures.node("sink", NodeType.END).build())
                .addEdge(EdgeDefinition.builder().sourceNodeId("router").targetNodeId("sink").build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("sourcePortId/targetPortId is required"));
    }

    @Test
    void rejectsWriteToUnknownStateField() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("typo-write")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .addNode(ValidationTestFixtures.node("research", NodeType.TOOL)
                        .addWrite("state.analysys")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("writes unknown state field: state.analysys"));
        assertThat(result.errors()).anyMatch(e -> e.contains("no state field 'analysys'"));
    }

    @Test
    void rejectsReadFromUnknownStateField() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("typo-read")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .addNode(ValidationTestFixtures.node("risk", NodeType.TOOL)
                        .addRead("state.analysys")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("reads unknown state field: state.analysys"));
    }

    @Test
    void rejectsStateReadWhenFieldNotDeclared() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("no-state-schema")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("agent", NodeType.TOOL)
                        .addRead("state.symbol")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("reads unknown state field: state.symbol"));
    }

    @Test
    void acceptsNestedAndIndexedStatePaths() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("nested-state")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .putState("news", StateFieldDefinition.builder().schema("News[]").build())
                .addNode(ValidationTestFixtures.node("scorer", NodeType.TOOL)
                        .addRead("state.analysis.score")
                        .addRead("state.news[0]")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void acceptsInputAndParameterReadPaths() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("input-param-reads")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").required(true).build())
                .putParameter("temperature", WorkflowParameterDefinition.builder().schema("number").build())
                .addNode(ValidationTestFixtures.node("llm", NodeType.MODEL)
                        .addRead("input.symbol")
                        .addRead("parameter.temperature")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void rejectsWriteToInputOrParameter() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("bad-write-root")
                .capability(ValidationTestFixtures.minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").build())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .addNode(ValidationTestFixtures.node("bad", NodeType.TOOL)
                        .addWrite("input.symbol")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("write path must use state. namespace"));
    }

    @Test
    void acceptsValidReadsAndWrites() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("valid-state-access")
                .capability(ValidationTestFixtures.minimalCapability())
                .putState("symbol", StateFieldDefinition.builder().schema("String").build())
                .putState("analysis", StateFieldDefinition.builder().schema("Analysis").build())
                .addNode(ValidationTestFixtures.node("research", NodeType.TOOL)
                        .addRead("state.symbol")
                        .addWrite("state.analysis")
                        .build())
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isTrue();
    }

    @Test
    void materializesRuntimeWhenJsonOmitsRuntimeBlock() throws Exception {
        WorkflowDefinition workflow =
                new JsonWorkflowSerializer()
                        .deserialize(
                                """
                                {
                                  "id": "planner",
                                  "capability": {
                                    "name": "Planner",
                                    "description": "Planner",
                                    "required_inputs": ["input"],
                                    "required_outputs": ["output"]
                                  },
                                  "nodes": []
                                }
                                """);

        assertThat(workflow.getRuntime()).isNotNull();
        assertThat(workflow.getRuntime().getContractVersion()).isEqualTo("1.0");
        assertThat(workflow.getRuntime().getExecutionModel()).isEqualTo(ExecutionModel.INLINE);
    }

    @Test
    void materializesExecutionModelWhenJsonRuntimeOmitsIt() throws Exception {
        WorkflowDefinition workflow =
                new JsonWorkflowSerializer()
                        .deserialize(
                                """
                                {
                                  "id": "agent",
                                  "capability": {
                                    "name": "Agent",
                                    "description": "Agent",
                                    "required_inputs": ["input"],
                                    "required_outputs": ["output"]
                                  },
                                  "runtime": {
                                    "contractVersion": "1.0",
                                    "capabilities": ["DEBUG"]
                                  },
                                  "nodes": []
                                }
                                """);

        assertThat(workflow.getRuntime().getExecutionModel()).isEqualTo(ExecutionModel.INLINE);
        assertThat(workflow.getRuntime().getCapabilities()).containsExactly(RuntimeCapability.DEBUG);
    }

    @Test
    void rejectsInvalidWorkflowRuntimeDefaultTimeout() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .capability(ValidationTestFixtures.minimalCapability())
                .runtime(WorkflowRuntimeDefinition.builder()
                        .executionModel(ExecutionModel.CHILD_WORKFLOW)
                        .defaultTimeout("not-a-duration")
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("runtime.defaultTimeout"));
    }

    @Test
    void rejectsAgentRegistryWithoutChildWorkflowExecutionModel() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent-registry")
                .capability(ValidationTestFixtures.minimalCapability())
                .addAgent(AgentDefinition.builder()
                        .id("research-agent")
                        .capability(CapabilityDefinition.builder()
                                .name("Research Agent")
                                .description("Research")
                                .build())
                        .workflow(WorkflowReferenceDefinition.builder()
                                .workflowId("research-agent")
                                .build())
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("runtime.executionModel CHILD_WORKFLOW"));
    }

    @Test
    void rejectsAgentNodeWithoutChildWorkflowExecutionModel() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent-node")
                .capability(ValidationTestFixtures.minimalCapability())
                .addNode(ValidationTestFixtures.node("agent", NodeType.AGENT)
                        .executionKind(ExecutionKind.SUBWORKFLOW)
                        .workflow(WorkflowReferenceDefinition.builder()
                                .workflowId("child")
                                .build())
                        .build())
                .build();

        ValidationResult result = WorkflowValidator.validate(workflow);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("execution.executionModel CHILD_WORKFLOW"));
    }

    @Test
    void validateOrThrowThrows() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("broken")
                .capability(ValidationTestFixtures.minimalCapability())
                .addEdge(EdgeDefinition.builder().sourceNodeId("a").targetNodeId("b").build())
                .build();

        assertThatThrownBy(() -> WorkflowValidator.validateOrThrow(workflow))
                .isInstanceOf(WorkflowValidationException.class);
    }
}
