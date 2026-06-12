package org.olo.definition.planner;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Accepts workflow-id shorthand ({@code "planner"}) or objects ({@code id}, future metadata fields).
 */
public final class AgentReferenceDefinitionDeserializer extends JsonDeserializer<AgentReferenceDefinition> {

    @Override
    public AgentReferenceDefinition deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("availableAgents entry is required");
        }
        if (node.isTextual()) {
            return AgentReferenceDefinition.of(node.textValue());
        }
        if (node.isObject()) {
            JsonNode idNode = node.get("id");
            if (idNode == null || idNode.isNull() || idNode.asText().isBlank()) {
                throw new IllegalArgumentException("availableAgents id is required");
            }
            return AgentReferenceDefinition.of(idNode.asText());
        }
        throw new IllegalArgumentException("unsupported availableAgents entry: " + node);
    }
}
