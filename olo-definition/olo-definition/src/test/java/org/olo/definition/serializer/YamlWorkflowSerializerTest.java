package org.olo.definition.serializer;

import org.olo.definition.validation.ValidationTestFixtures;
import org.olo.definition.workflow.WorkflowBuilder;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YamlWorkflowSerializerTest {

    private final YamlWorkflowSerializer serializer = new YamlWorkflowSerializer();

    @Test
    void roundTripsWorkflow() throws Exception {
        WorkflowDefinition original = WorkflowBuilder.create("StockAnalysis")
                .id("stock-analysis")
                .capability(ValidationTestFixtures.minimalCapability())
                .inputNode("input")
                .outputNode("output")
                .connect("input", "output")
                .build();

        String yaml = serializer.serialize(original);
        assertThat(yaml).contains("stock-analysis");
        assertThat(yaml).contains("INPUT");

        WorkflowDefinition restored = serializer.deserialize(yaml);
        assertThat(restored).isEqualTo(original);
    }
}
