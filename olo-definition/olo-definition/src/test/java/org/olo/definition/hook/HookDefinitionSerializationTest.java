package org.olo.definition.hook;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.hook.NodeHooksDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.serializer.YamlWorkflowSerializer;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookDefinitionSerializationTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();
    private final YamlWorkflowSerializer yaml = new YamlWorkflowSerializer();

    @Test
    void jsonRoundTripPreservesHooks() throws Exception {
        WorkflowDefinition original = workflowWithHooks();
        WorkflowValidator.validateOrThrow(original);

        WorkflowDefinition restored = json.deserialize(json.serialize(original));

        assertThat(restored.getHooks()).hasSize(3);
        assertThat(restored.getHooks().get(0).getId()).isEqualTo("tracing");
        assertThat(restored.getHooks().get(0).getPattern()).isEqualTo("analysis.*");
        assertThat(restored.getHooks().get(0).getPre().getImplementationId()).isEqualTo("tracing-start");
        assertThat(restored.getHooks().get(0).getOnFinally().getImplementationId()).isEqualTo("tracing-end");

        assertThat(restored.getHooks().get(1).getId()).isEqualTo("metrics");
        assertThat(restored.getHooks().get(1).getPattern()).isEqualTo("**");

        assertThat(restored.getHooks().get(2).getId()).isEqualTo("audit");
        assertThat(restored.getHooks().get(2).getOnError().getImplementationId()).isEqualTo("audit-error");
        assertThat(restored.getHooks().get(2).getPre()).isNull();
    }

    @Test
    void yamlRoundTripPreservesHooks() throws Exception {
        WorkflowDefinition original = workflowWithHooks();
        WorkflowDefinition restored = yaml.deserialize(yaml.serialize(original));
        assertThat(restored).isEqualTo(original);
        assertThat(WorkflowValidator.validate(restored).valid()).isTrue();
    }

    @Test
    void rejectsHookWithoutPhases() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Hooks")
                .id("hooks-invalid")
                .capability(minimalCapability())
                .hook(HookDefinition.builder().id("empty").pattern("**").build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        assertThat(WorkflowValidator.validate(workflow).valid()).isFalse();
        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("at least one phase"));
    }

    @Test
    void jsonRoundTripPreservesNodeHooks() throws Exception {
        WorkflowDefinition original = workflowWithNodeHooks();
        WorkflowValidator.validateOrThrow(original);

        WorkflowDefinition restored = json.deserialize(json.serialize(original));
        NodeDefinition llm1 = restored.getNodes().stream()
                .filter(n -> "llm1".equals(n.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(llm1.getHooks().getPre()).hasSize(1);
        assertThat(llm1.getHooks().getPre().get(0).getImplementationId()).isEqualTo("prompt-validator");
        assertThat(llm1.getHooks().getOnError().get(0).getImplementationId()).isEqualTo("model-failure-alert");
        assertThat(llm1.getHooks().getOnFinally().get(0).getImplementationId()).isEqualTo("cleanup");
    }

    @Test
    void rejectsNodeHookNotRegisteredAtWorkflowLevel() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Node Hooks")
                .id("node-hooks-invalid")
                .capability(minimalCapability())
                .hook(HookDefinition.builder()
                        .id("metrics")
                        .pattern("**")
                        .pre(HookActionDefinition.builder().implementationId("metrics-start").build())
                        .build())
                .addNode(ValidationTestFixtures.node("llm1", NodeType.MODEL)
                        .hooks(NodeHooksDefinition.builder()
                                .addPre(HookActionDefinition.builder().implementationId("unknown-hook").build())
                                .build())
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "llm1")
                .connect("llm1", "output")
                .build();

        assertThat(WorkflowValidator.validate(workflow).errors())
                .anyMatch(e -> e.contains("not registered on any workflow-level hook"));
    }

    @Test
    void rejectsDuplicateHookIds() {
        HookDefinition hook = HookDefinition.builder()
                .id("dup")
                .pattern("**")
                .pre(HookActionDefinition.builder().implementationId("a").build())
                .build();

        WorkflowDefinition workflow = WorkflowBuilder.create("Hooks")
                .id("hooks-dup")
                .capability(minimalCapability())
                .hook(hook)
                .hook(hook)
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        assertThat(WorkflowValidator.validate(workflow).errors()).anyMatch(e -> e.contains("duplicate hook id"));
    }

    private static WorkflowDefinition workflowWithNodeHooks() {
        return WorkflowBuilder.create("LLM Workflow")
                .id("llm-workflow")
                .capability(minimalCapability())
                .hook(HookDefinition.builder()
                        .id("prompt-validator")
                        .pattern("llm1")
                        .pre(HookActionDefinition.builder().implementationId("prompt-validator").build())
                        .build())
                .hook(HookDefinition.builder()
                        .id("model-failure-alert")
                        .pattern("llm1")
                        .onError(HookActionDefinition.builder().implementationId("model-failure-alert").build())
                        .build())
                .hook(HookDefinition.builder()
                        .id("cleanup")
                        .pattern("llm1")
                        .onFinally(HookActionDefinition.builder().implementationId("cleanup").build())
                        .build())
                .addNode(ValidationTestFixtures.node("llm1", NodeType.MODEL)
                        .hooks(NodeHooksDefinition.builder()
                                .addPre(HookActionDefinition.builder().implementationId("prompt-validator").build())
                                .addOnError(HookActionDefinition.builder().implementationId("model-failure-alert").build())
                                .addOnFinally(HookActionDefinition.builder().implementationId("cleanup").build())
                                .build())
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "llm1")
                .connect("llm1", "output")
                .build();
    }

    private static WorkflowDefinition workflowWithHooks() {
        return WorkflowBuilder.create("Hooked Workflow")
                .id("hooked-workflow")
                .capability(minimalCapability())
                .hook(HookDefinition.builder()
                        .id("tracing")
                        .pattern("analysis.*")
                        .pre(HookActionDefinition.builder().implementationId("tracing-start").build())
                        .onFinally(HookActionDefinition.builder().implementationId("tracing-end").build())
                        .build())
                .hook(HookDefinition.builder()
                        .id("metrics")
                        .pattern("**")
                        .pre(HookActionDefinition.builder().implementationId("metrics-start").build())
                        .onFinally(HookActionDefinition.builder().implementationId("metrics-stop").build())
                        .build())
                .hook(HookDefinition.builder()
                        .id("audit")
                        .pattern("trading.*")
                        .onError(HookActionDefinition.builder().implementationId("audit-error").build())
                        .build())
                .addNode(ValidationTestFixtures.node("analysis-step", NodeType.MODEL).build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "analysis-step")
                .connect("analysis-step", "output")
                .build();
    }

    private static CapabilityDefinition minimalCapability() {
        return CapabilityDefinition.builder()
                .name("Test")
                .description("Test workflow")
                .addInput("in")
                .addOutput("out")
                .build();
    }
}
