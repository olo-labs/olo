package org.olo.definition.parameter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.spi.catalog.ParameterWidget;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accepts full UI parameter objects, legacy {@code schema} keys, or shorthand literals.
 */
public final class WorkflowParameterDefinitionDeserializer extends JsonDeserializer<WorkflowParameterDefinition> {

    @Override
    public WorkflowParameterDefinition deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode node = mapper.readTree(parser);
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("workflow parameter value is required");
        }
        if (node.isObject()) {
            return fromObject(node);
        }
        return fromLiteral(node);
    }

    private static WorkflowParameterDefinition fromObject(JsonNode node) {
        WorkflowParameterDefinition.Builder builder = WorkflowParameterDefinition.builder();
        JsonNode type = node.hasNonNull("type") ? node.get("type") : node.get("schema");
        if (type != null && !type.isNull()) {
            builder.type(type.asText());
        }
        JsonNode label = node.get("label");
        if (label != null && !label.isNull()) {
            builder.label(label.asText());
        }
        JsonNode defaultValue = node.get("defaultValue");
        if (defaultValue != null && !defaultValue.isNull()) {
            builder.defaultValue(readLiteralValue(defaultValue));
        }
        JsonNode description = node.get("description");
        if (description != null && !description.isNull()) {
            builder.description(description.asText());
        }
        JsonNode validation = node.get("validation");
        if (validation != null && validation.isObject()) {
            applyValidation(builder, validation);
        }
        JsonNode minimum = node.get("minimum");
        if (minimum != null && minimum.isNumber()) {
            builder.minimum(minimum.doubleValue());
        }
        JsonNode maximum = node.get("maximum");
        if (maximum != null && maximum.isNumber()) {
            builder.maximum(maximum.doubleValue());
        }
        JsonNode step = node.get("step");
        if (step != null && step.isNumber()) {
            builder.step(step.doubleValue());
        }
        JsonNode minLength = node.get("minLength");
        if (minLength != null && minLength.isIntegralNumber()) {
            builder.minLength(minLength.intValue());
        }
        JsonNode maxLength = node.get("maxLength");
        if (maxLength != null && maxLength.isIntegralNumber()) {
            builder.maxLength(maxLength.intValue());
        }
        JsonNode required = node.get("required");
        if (required != null && required.isBoolean()) {
            builder.required(required.booleanValue());
        }
        JsonNode visibleWhen = node.get("visibleWhen");
        if (visibleWhen != null && visibleWhen.isObject()) {
            Map<String, String> conditions = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = visibleWhen.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                conditions.put(field.getKey(), field.getValue().asText());
            }
            if (!conditions.isEmpty()) {
                builder.visibleWhen(conditions);
            }
        }
        JsonNode ui = node.get("ui");
        if (ui != null && ui.isObject()) {
            ParameterUiDefinition.Builder uiBuilder = ParameterUiDefinition.builder();
            if (ui.hasNonNull("widget")) {
                uiBuilder.widget(ParameterWidget.normalizeCatalogValue(ui.get("widget").asText()));
            }
            if (ui.hasNonNull("group")) {
                uiBuilder.group(ui.get("group").asText());
            }
            if (ui.hasNonNull("help")) {
                uiBuilder.help(ui.get("help").asText());
            }
            if (ui.hasNonNull("placeholder")) {
                uiBuilder.placeholder(ui.get("placeholder").asText());
            }
            if (ui.hasNonNull("order")) {
                uiBuilder.order(ui.get("order").asInt());
            }
            builder.ui(uiBuilder.build());
        }
        return builder.build();
    }

    private static void applyValidation(WorkflowParameterDefinition.Builder builder, JsonNode validation) {
        JsonNode minLength = validation.get("minLength");
        if (minLength != null && minLength.isIntegralNumber()) {
            builder.minLength(minLength.intValue());
        }
        JsonNode maxLength = validation.get("maxLength");
        if (maxLength != null && maxLength.isIntegralNumber()) {
            builder.maxLength(maxLength.intValue());
        }
        JsonNode minimum = validation.get("minimum");
        if (minimum != null && minimum.isNumber()) {
            builder.minimum(minimum.doubleValue());
        }
        JsonNode maximum = validation.get("maximum");
        if (maximum != null && maximum.isNumber()) {
            builder.maximum(maximum.doubleValue());
        }
        JsonNode step = validation.get("step");
        if (step != null && step.isNumber()) {
            builder.step(step.doubleValue());
        }
    }

    private static Object readLiteralValue(JsonNode node) {
        if (node.isIntegralNumber()) {
            return node.intValue();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static WorkflowParameterDefinition fromLiteral(JsonNode node) {
        WorkflowParameterDefinition.Builder builder = WorkflowParameterDefinition.builder();
        if (node.isIntegralNumber()) {
            return builder.type("integer").defaultValue(node.intValue()).build();
        }
        if (node.isFloatingPointNumber() || node.isBigDecimal()) {
            return builder.type("number").defaultValue(node.doubleValue()).build();
        }
        if (node.isBoolean()) {
            return builder.type("boolean").defaultValue(node.booleanValue()).build();
        }
        if (node.isTextual()) {
            return builder.type("string").defaultValue(node.textValue()).build();
        }
        throw new IllegalArgumentException("unsupported workflow parameter literal: " + node);
    }
}
