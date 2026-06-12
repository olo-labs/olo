package org.olo.definition.configuration;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.designer.DesignerDefinition;
import org.olo.definition.execution.ExecutionModel;
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

    private static CapabilityDefinition passThroughCapability(String name, String description, String tag) {
        return CapabilityDefinition.builder()
                .name(name)
                .description(description)
                .addTag(tag)
                .addInput("input")
                .addOutput("output")
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

    private static WorkflowDefinition chatProfile(
            String id, String name, String shortDescription, String emoji) {
        return build(WorkflowBuilder.create(name)
                .id(id)
                .role(name)
                .shortDescription(shortDescription)
                .emoji(emoji)
                .designer(agentDesigner(id, name.toLowerCase()))
                .queue(id)
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .capability(passThroughCapability(name, shortDescription, id))
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .metadata("description", shortDescription)
                .metadata("role", id));
    }

    static WorkflowDefinition agent() {
        String description = "Autonomous tool-using agent";
        return build(WorkflowBuilder.create("Agent")
                .id("agent")
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
                .capability(passThroughCapability("Agent", description, "agent"))
                .metadata("description", description)
                .agentPlannerMetadata()
                .agentParameters()
                .agentAvailableAgents()
                .agentDelegation()
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output"));
    }

    static WorkflowDefinition architect() {
        return chatProfile(
                "architect",
                "Architect",
                "System design and architecture guidance",
                "🏗️");
    }

    static WorkflowDefinition ask() {
        return chatProfile("ask", "Ask", "Direct questions and clear answers", "❓");
    }

    static WorkflowDefinition debug() {
        String description = "Verbose output for troubleshooting";
        return build(WorkflowBuilder.create("Debug")
                .id("debug")
                .role("Debug")
                .shortDescription(description)
                .emoji("🐛")
                .queue("debug")
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .executionModel(ExecutionModel.INLINE)
                .debuggable()
                .replayable()
                .capability(passThroughCapability("Debug", description, "debug"))
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .metadata("description", description)
                .metadata("role", "debug"));
    }

    static WorkflowDefinition detailed() {
        return chatProfile("detailed", "Detailed", "Thorough, in-depth explanations", "📖");
    }

    static WorkflowDefinition fast() {
        return chatProfile("fast", "Fast", "Quick, concise responses", "⚡");
    }

    static WorkflowDefinition planner() {
        return chatProfile("planner", "Planner", "Structured plans and task breakdowns", "📋");
    }

    static WorkflowDefinition reviewer() {
        return chatProfile("reviewer", "Reviewer", "Review code and content critically", "🔍");
    }

    static WorkflowDefinition strict() {
        return chatProfile("strict", "Strict", "Precise, rule-following responses", "📏");
    }

    static WorkflowDefinition summary() {
        return chatProfile("summary", "Summary", "Brief summaries and key points", "📝");
    }

    static WorkflowDefinition teacher() {
        return chatProfile("teacher", "Teacher", "Learn concepts step by step", "🎓");
    }

    /** Minimal echo task-queue preset ({@code workflow.json} on disk, id {@code minimal-echo}). */
    static WorkflowDefinition workflow() {
        String description = "Smallest valid OLO workflow: passes input through to output.";
        return build(WorkflowBuilder.create("Minimal Echo")
                .id("minimal-echo")
                .role("Echo")
                .shortDescription("Minimal passthrough workflow")
                .emoji("💬")
                .queue("minimal-echo")
                .workflowType("olo")
                .runAgain(true)
                .version("1.0.0")
                .capability(CapabilityDefinition.builder()
                        .name("Minimal Echo")
                        .description(description)
                        .addInput("input")
                        .addOutput("output")
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .metadata("description", description));
    }
}
