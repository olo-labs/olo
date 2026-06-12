package org.olo.definition.designer;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DesignerDefinitionTest {

    private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

    @Test
    void roundTripsDesignerOnWorkflow() throws Exception {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("agent")
                .designer(DesignerDefinition.builder()
                        .paletteGroup("Agents")
                        .searchKeyword("planning")
                        .searchKeyword("task")
                        .nodeSize(300, 120)
                        .resizable(true)
                        .draggable(true)
                        .build())
                .capability(CapabilityDefinition.builder()
                        .name("Agent")
                        .description("test")
                        .build())
                .build();

        String serialized = json.serialize(workflow);
        assertThat(serialized).contains("\"designer\"");
        assertThat(serialized).contains("\"paletteGroup\" : \"Agents\"");

        WorkflowDefinition restored = json.deserialize(serialized);
        assertThat(restored.getDesigner().getPaletteGroup()).isEqualTo("Agents");
        assertThat(restored.getDesigner().getSearchKeywords()).containsExactly("planning", "task");
        assertThat(restored.getDesigner().getNodeSize().getWidth()).isEqualTo(300);
        assertThat(restored.getDesigner().getNodeSize().getHeight()).isEqualTo(120);
    }

    @Test
    void migratesLegacyFlatSizeFieldsOnDeserialize() throws Exception {
        String raw = """
                {
                  "id": "agent",
                  "designer": {
                    "paletteGroup": "Agents",
                    "defaultWidth": 300,
                    "defaultHeight": 120
                  },
                  "capability": {
                    "name": "Agent",
                    "description": "test"
                  }
                }
                """;

        WorkflowDefinition workflow = json.deserialize(raw);
        assertThat(workflow.getDesigner().getNodeSize().getWidth()).isEqualTo(300);
        assertThat(workflow.getDesigner().getNodeSize().getHeight()).isEqualTo(120);
    }
}
