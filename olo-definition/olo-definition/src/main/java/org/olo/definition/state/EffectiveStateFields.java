package org.olo.definition.state;

import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.workflow.WorkflowDefinition;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the effective workflow state field names available at runtime, including inputs that
 * {@linkplain WorkflowInputDefinition#isPopulateState() auto-populate} into state.
 */
public final class EffectiveStateFields {

    private EffectiveStateFields() {
    }

    /**
     * Union of explicit {@code state} keys and input keys with {@code populateState != false}.
     */
    public static Set<String> names(WorkflowDefinition workflow) {
        Set<String> names = new LinkedHashSet<>(workflow.getState().keySet());
        for (Map.Entry<String, WorkflowInputDefinition> entry : workflow.getInputs().entrySet()) {
            WorkflowInputDefinition input = entry.getValue();
            if (input != null && input.isPopulateState()) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(names);
    }

    public static void validateDeclarations(WorkflowDefinition workflow, List<String> errors) {
        for (Map.Entry<String, WorkflowInputDefinition> entry : workflow.getInputs().entrySet()) {
            String name = entry.getKey();
            WorkflowInputDefinition input = entry.getValue();
            if (input == null || !input.isPopulateState()) {
                continue;
            }
            if (workflow.getState().containsKey(name)) {
                errors.add("input '" + name + "' auto-populates state; remove redundant state."
                        + name + " or set populateState: false on the input");
            }
        }
    }
}
