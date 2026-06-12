package org.olo.definition.parameter;

import org.olo.annotation.catalog.ParameterDescriptor;
import org.olo.annotation.catalog.ParameterUiDescriptor;
import org.olo.annotation.catalog.ParameterValidationDescriptor;

/**
 * Maps catalog {@link ParameterDescriptor} entries (Studio / {@code workflow-presets.json}) to runtime
 * {@link WorkflowParameterDefinition} blocks on {@link org.olo.definition.workflow.WorkflowDefinition}.
 */
final class ParameterCatalogMapper {

    private ParameterCatalogMapper() {}

    static WorkflowParameterDefinition toWorkflowParameter(ParameterDescriptor descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("parameter descriptor is required");
        }
        WorkflowParameterDefinition.Builder builder = WorkflowParameterDefinition.builder()
                .type(descriptor.type)
                .label(descriptor.label)
                .description(descriptor.description)
                .defaultValue(normalizeDefaultValue(descriptor.type, descriptor.defaultValue))
                .required(descriptor.required)
                .visibleWhen(descriptor.visibleWhen);
        if (descriptor.validation != null) {
            builder.validation(toValidation(descriptor.validation));
        }
        if (descriptor.ui != null) {
            builder.ui(toUi(descriptor.ui));
        }
        return builder.build();
    }

  /** Catalog omits blank string defaults ({@code NON_NULL}); workflow JSON keeps {@code ""}. */
    private static Object normalizeDefaultValue(String type, Object defaultValue) {
        if (defaultValue != null) {
            return defaultValue;
        }
        return "string".equals(type) ? "" : null;
    }

    private static ParameterValidationDefinition toValidation(ParameterValidationDescriptor validation) {
        return ParameterValidationDefinition.builder()
                .minLength(validation.minLength)
                .maxLength(validation.maxLength)
                .minimum(validation.minimum)
                .maximum(validation.maximum)
                .step(validation.step)
                .build();
    }

    private static ParameterUiDefinition toUi(ParameterUiDescriptor ui) {
        ParameterUiDefinition.Builder builder = ParameterUiDefinition.builder();
        if (ui.widget != null) {
            builder.widget(ui.widget);
        }
        builder.group(ui.group).help(ui.help).placeholder(ui.placeholder).order(ui.order);
        return builder.build();
    }
}
