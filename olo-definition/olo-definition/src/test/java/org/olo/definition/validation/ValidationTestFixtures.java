package org.olo.definition.validation;

import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.port.PortDefinition;
import org.olo.definition.port.PortDirection;

/**
 * Shared capability blocks for workflow validation tests.
 */
public final class ValidationTestFixtures {

    private ValidationTestFixtures() {
    }

    public static CapabilityDefinition minimalCapability() {
        return CapabilityDefinition.builder()
                .name("Test Workflow")
                .description("Test workflow for structural validation")
                .addInput("input")
                .addOutput("output")
                .build();
    }

    public static NodeDefinition.Builder node(String id, NodeType type) {
        NodeDefinition.Builder builder = NodeDefinition.builder().id(id).type(type);
        if (type != NodeType.INPUT) {
            builder.addPort(PortDefinition.inputPort("in", "any"));
        }
        if (type != NodeType.OUTPUT) {
            builder.addPort(PortDefinition.outputPort("out", "any"));
        }
        return builder;
    }
}
