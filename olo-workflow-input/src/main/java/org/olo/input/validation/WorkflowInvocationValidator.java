package org.olo.input.validation;

import org.olo.input.consumer.WorkflowInputValues;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.workflow.WorkflowDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates a deserialized worker input payload against {@link WorkflowDefinition#getInputs()}.
 */
public final class WorkflowInvocationValidator {

    private WorkflowInvocationValidator() {
    }

    public static WorkflowInvocationValidationResult validate(
            WorkflowDefinition workflow, WorkflowInputValues values) {
        List<String> errors = new ArrayList<>();
        if (workflow == null) {
            return WorkflowInvocationValidationResult.failure(List.of("workflow definition is required"));
        }
        if (values == null) {
            return WorkflowInvocationValidationResult.failure(List.of("workflow input values are required"));
        }

        for (Map.Entry<String, WorkflowInputDefinition> entry : workflow.getInputs().entrySet()) {
            String name = entry.getKey();
            WorkflowInputDefinition declared = entry.getValue();
            if (declared == null) {
                continue;
            }
            if (declared.isRequired() && values.getStringValue(name).isEmpty()) {
                errors.add("required workflow input missing: " + name);
            }
        }

        return errors.isEmpty()
                ? WorkflowInvocationValidationResult.success()
                : WorkflowInvocationValidationResult.failure(errors);
    }
}
