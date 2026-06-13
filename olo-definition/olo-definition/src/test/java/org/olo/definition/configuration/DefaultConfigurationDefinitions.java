package org.olo.definition.configuration;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.designer.DesignerDefinition;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.preset.WorkflowPresetInfrastructure;
import org.olo.definition.workflow.ChildWorkflowDefinition;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;

/**
 * Programmatic builders for preset workflows under {@code olo-configuration/default/}.
 */
final class DefaultConfigurationDefinitions {

    private DefaultConfigurationDefinitions() {
    }

    private static WorkflowDefinition build(WorkflowBuilder builder) {
        return builder.withStandardReturnVariable().build();
    }

    private static CapabilityDefinition agentCapability(String name, String description, String tag) {
        return CapabilityDefinition.builder()
                .name(name)
                .description(description)
                .addTag(tag)
                .addInput("input")
                .addOutput("output")
                .addRequiredContext(WorkflowPresetInfrastructure.MESSAGE_VARIABLE)
                .build();
    }

    private static DesignerDefinition agentDesigner(String... searchKeywords) {
        DesignerDefinition.Builder builder = DesignerDefinition.builder()
                .paletteGroup("Agents")
                .resizable(true)
                .draggable(true);
        for (String keyword : searchKeywords) {
            builder.searchKeyword(keyword);
        }
        return builder.build();
    }

    private static WorkflowDefinition agentPreset(
            String id, String name, String shortDescription, String emoji, String... searchKeywords) {
        return build(WorkflowBuilder.create(name)
                .id(id)
                .enabled(true)
                .isDefault(true)
                .role(name)
                .shortDescription(shortDescription)
                .emoji(emoji)
                .designer(agentDesigner(searchKeywords))
                .queue(id)
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .executionModel(ExecutionModel.INLINE)
                .capability(agentCapability(name, shortDescription, id))
                .withMessageContract()
                .defaultLocalModelInfrastructure()
                .presetPlannerPrompts(id)
                .presetPlannerContext(id)
                .agentCanvasPipeline(id)
                .metadata("description", shortDescription)
                .metadata("role", id));
    }

    static WorkflowDefinition agent() {
        String description = "Autonomous tool-using agent";
        return build(WorkflowBuilder.create("Agent")
                .id("agent")
                .enabled(true)
                .isDefault(true)
                .role("Agent")
                .shortDescription(description)
                .emoji("🤖")
                .designer(DesignerDefinition.builder()
                        .paletteGroup("Agents")
                        .searchKeyword("planning")
                        .searchKeyword("task")
                        .searchKeyword("agent")
                        .nodeSize(300, 120)
                        .resizable(true)
                        .draggable(true)
                        .build())
                .queue("agent")
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .agentWorkflowRuntime()
                .capability(agentCapability("Agent", description, "agent"))
                .withMessageContract()
                .defaultLocalModelInfrastructure()
                .agentPlannerPrompts()
                .agentPlannerMetadata()
                .agentPlannerContext()
                .agentParameters()
                .agentAvailableAgents()
                .agentDelegation()
                .childWorkflow(ChildWorkflowDefinition.builder()
                        .workflowId("planner")
                        .workflowVersion("1.0.0")
                        .build())
                .childWorkflow(ChildWorkflowDefinition.builder()
                        .workflowId("fast")
                        .workflowVersion("1.0.0")
                        .build())
                .childWorkflow(ChildWorkflowDefinition.builder()
                        .workflowId("detailed")
                        .workflowVersion("1.0.0")
                        .build())
                .childWorkflow(ChildWorkflowDefinition.builder()
                        .workflowId("reviewer")
                        .workflowVersion("1.0.0")
                        .build())
                .agentCanvasPipeline("agent")
                .metadata("description", description));
    }

    static WorkflowDefinition architect() {
        return build(WorkflowBuilder.from(agentPreset(
                        "architect",
                        "Architect",
                        "System design and architecture guidance",
                        "🏗️",
                        "architect"))
                .enabled(false));
    }

    static WorkflowDefinition ask() {
        return agentPreset("ask", "Ask", "Direct questions and clear answers", "❓", "ask");
    }

    static WorkflowDefinition debug() {
        String description = "Verbose output for troubleshooting";
        return build(WorkflowBuilder.create("Debug")
                .id("debug")
                .enabled(true)
                .isDefault(true)
                .role("Debug")
                .shortDescription(description)
                .emoji("🐛")
                .designer(agentDesigner("debug"))
                .queue("debug")
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .executionModel(ExecutionModel.INLINE)
                .debuggable()
                .replayable()
                .capability(agentCapability("Debug", description, "debug"))
                .withMessageContract()
                .defaultLocalModelInfrastructure()
                .presetPlannerPrompts("debug")
                .presetPlannerContext("debug")
                .agentCanvasPipeline("debug")
                .metadata("description", description)
                .metadata("role", "debug"));
    }

    static WorkflowDefinition detailed() {
        return agentPreset("detailed", "Detailed", "Thorough, in-depth explanations", "📖", "detailed");
    }

    static WorkflowDefinition fast() {
        return agentPreset("fast", "Fast", "Quick, concise responses", "⚡", "fast");
    }

    static WorkflowDefinition planner() {
        return agentPreset(
                "planner",
                "Planner",
                "Structured plans and task breakdowns",
                "📋",
                "planner");
    }

    static WorkflowDefinition reviewer() {
        return agentPreset("reviewer", "Reviewer", "Review code and content critically", "🔍", "reviewer");
    }

    static WorkflowDefinition strict() {
        return agentPreset("strict", "Strict", "Precise, rule-following responses", "📏", "strict");
    }

    static WorkflowDefinition summary() {
        return agentPreset("summary", "Summary", "Brief summaries and key points", "📝", "summary");
    }

    static WorkflowDefinition teacher() {
        return agentPreset("teacher", "Teacher", "Learn concepts step by step", "🎓", "teacher");
    }

    /** Minimal echo task-queue preset ({@code workflow.json} on disk, id {@code minimal-echo}). */
    static WorkflowDefinition workflow() {
        String description = "Smallest valid OLO workflow: passes input through to output.";
        return build(WorkflowBuilder.create("Minimal Echo")
                .id("minimal-echo")
                .enabled(true)
                .isDefault(true)
                .role("Echo")
                .shortDescription("Minimal passthrough workflow")
                .emoji("💬")
                .queue("minimal-echo")
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .executionModel(ExecutionModel.INLINE)
                .capability(CapabilityDefinition.builder()
                        .name("Minimal Echo")
                        .description(description)
                        .addInput("input")
                        .addOutput("output")
                        .addRequiredContext(WorkflowPresetInfrastructure.MESSAGE_VARIABLE)
                        .build())
                .withMessageContract()
                .defaultLocalModelInfrastructure()
                .presetPlannerPrompts("minimal-echo")
                .presetPlannerContext("minimal-echo")
                .agentCanvasPipeline("minimal-echo")
                .metadata("description", description));
    }
}
