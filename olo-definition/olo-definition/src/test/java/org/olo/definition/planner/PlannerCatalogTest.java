package org.olo.definition.planner;

import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.validation.WorkflowValidator;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerCatalogTest {

    @Test
    void buildsPlannerViewWithoutGraphOrConfiguration() {
        WorkflowDefinition workflow = WorkflowBuilder.create("Stock Analysis")
                .id("stock-analysis")
                .capability(CapabilityDefinition.builder()
                        .name("Stock Analysis Workflow")
                        .description("Produces investment analysis.")
                        .addInput("symbol")
                        .addOutput("analysis")
                        .build())
                .tool(ToolDefinition.builder()
                        .id("screener-tool")
                        .capability(CapabilityDefinition.builder()
                                .name("Stock Screener")
                                .description("Filters stocks by technical criteria.")
                                .addExample("Find breakout candidates")
                                .build())
                        .putConfiguration("implementation", "com.example.Screener")
                        .build())
                .agent(AgentDefinition.builder()
                        .id("research-agent")
                        .capability(CapabilityDefinition.builder()
                                .name("Research Agent")
                                .description("Research topics and summarize findings")
                                .build())
                        .workflow(WorkflowReferenceDefinition.builder()
                                .workflowId("research-agent")
                                .build())
                        .executionModel(ExecutionModel.CHILD_WORKFLOW)
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        WorkflowValidator.validateOrThrow(workflow);

        PlannerCatalog catalog = PlannerCatalog.from(workflow);

        assertThat(catalog.getWorkflows()).hasSize(1);
        assertThat(catalog.getTools()).extracting(PlannerCapability::getName)
                .containsExactly("Stock Screener");
        assertThat(catalog.getAgents()).extracting(PlannerCapability::getName)
                .containsExactly("Research Agent");
        assertThat(catalog.getNodes()).isEmpty();

        assertThat(catalog.plannerView())
                .extracting(PlannerCapability::getName)
                .containsExactly("Stock Analysis Workflow", "Research Agent", "Stock Screener");

        PlannerCapability workflowCapability = catalog.plannerView().get(0);
        assertThat(workflowCapability.getDescription()).isEqualTo("Produces investment analysis.");
        assertThat(workflowCapability.getRequiredInputs()).containsExactly("symbol");
        assertThat(catalog.getTools().get(0).getExamples()).contains("Find breakout candidates");
    }

    @Test
    void plannerViewIncludesOperationalMetadata() throws Exception {
        WorkflowDefinition workflow = WorkflowBuilder.create("Research Agent")
                .id("research-agent")
                .capability(CapabilityDefinition.builder()
                        .name("Research Agent")
                        .description("Research topics and summarize findings")
                        .addInput("query")
                        .addOutput("summary")
                        .addToolRequirement("web-search")
                        .addRequiredContext("user_profile")
                        .cost(0.15)
                        .latency(45_000.0)
                        .confidence(0.85)
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        PlannerCapability planner = PlannerCatalog.from(workflow).plannerView().get(0);

        assertThat(planner.getCost()).isEqualTo(0.15);
        assertThat(planner.getLatency()).isEqualTo(45_000.0);
        assertThat(planner.getConfidence()).isEqualTo(0.85);
        assertThat(planner.getToolRequirements()).containsExactly("web-search");
        assertThat(planner.getRequiredContext()).containsExactly("user_profile");
        assertThat(planner.getRequiredInputs()).containsExactly("query");
    }

    @Test
    void excludesNodeCapabilitiesEvenWhenDeclared() {
        WorkflowDefinition workflow = WorkflowBuilder.create("With Node Capability")
                .id("node-cap-workflow")
                .capability(CapabilityDefinition.builder()
                        .name("Parent")
                        .description("Parent workflow")
                        .addInput("q")
                        .addOutput("a")
                        .build())
                .addNode(ValidationTestFixtures.node("llm", org.olo.definition.node.NodeType.MODEL)
                        .capability(CapabilityDefinition.builder()
                                .name("Duplicate Model Capability")
                                .description("Should not appear in planner catalog V1")
                                .addInput("prompt")
                                .addOutput("text")
                                .build())
                        .build())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        PlannerCatalog catalog = PlannerCatalog.from(workflow);

        assertThat(catalog.getNodes()).isEmpty();
        assertThat(catalog.plannerView()).hasSize(1);
        assertThat(catalog.plannerView().get(0).getName()).isEqualTo("Parent");
    }
}
