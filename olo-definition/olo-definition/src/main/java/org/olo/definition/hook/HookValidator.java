package org.olo.definition.hook;

import java.util.List;
import java.util.Set;

/**
 * Structural validation for workflow and node {@link HookDefinition} bindings.
 */
public final class HookValidator {

    private HookValidator() {
    }

    public static void validate(String ownerLabel, HookDefinition hook, List<String> errors) {
        if (hook == null) {
            errors.add(ownerLabel + ": hook entry must not be null");
            return;
        }
        if (isBlank(hook.getId())) {
            errors.add(ownerLabel + ": hook id is required");
        }
        if (isBlank(hook.getPattern())) {
            errors.add(ownerLabel + ": hook pattern is required");
        }

        boolean hasPre = hook.getPre() != null;
        boolean hasOnError = hook.getOnError() != null;
        boolean hasFinally = hook.getOnFinally() != null;

        if (!hasPre && !hasOnError && !hasFinally) {
            errors.add(ownerLabel + " hook '" + hook.getId() + "': at least one phase (pre, onError, finally) is required");
        }

        if (hasPre) {
            validateAction(ownerLabel, hook.getId(), HookPhase.PRE, hook.getPre(), errors);
        }
        if (hasOnError) {
            validateAction(ownerLabel, hook.getId(), HookPhase.ON_ERROR, hook.getOnError(), errors);
        }
        if (hasFinally) {
            validateAction(ownerLabel, hook.getId(), HookPhase.FINALLY, hook.getOnFinally(), errors);
        }
    }

    private static void validateAction(
            String ownerLabel,
            String hookId,
            HookPhase phase,
            HookActionDefinition action,
            List<String> errors) {
        if (action == null) {
            errors.add(ownerLabel + " hook '" + hookId + "': " + phase.name().toLowerCase() + " action must not be null");
            return;
        }
        if (isBlank(action.getImplementationId())) {
            errors.add(
                    ownerLabel
                            + " hook '"
                            + hookId
                            + "': "
                            + phaseJsonName(phase)
                            + " requires implementationId");
        }
    }

    public static void validateNodeHooks(
            String ownerLabel,
            String nodeId,
            NodeHooksDefinition hooks,
            Set<String> workflowHookImplementationIds,
            List<String> errors) {
        if (hooks == null) {
            return;
        }

        boolean hasPre = !hooks.getPre().isEmpty();
        boolean hasOnError = !hooks.getOnError().isEmpty();
        boolean hasFinally = !hooks.getOnFinally().isEmpty();

        if (!hasPre && !hasOnError && !hasFinally) {
            errors.add(ownerLabel + " node '" + nodeId + "': hooks requires at least one phase (pre, onError, finally)");
        }

        if (workflowHookImplementationIds.isEmpty() && (hasPre || hasOnError || hasFinally)) {
            errors.add(
                    ownerLabel
                            + " node '"
                            + nodeId
                            + "': node hooks require at least one workflow-level hook to register implementation ids");
        }

        validateNodePhaseActions(ownerLabel, nodeId, HookPhase.PRE, hooks.getPre(), workflowHookImplementationIds, errors);
        validateNodePhaseActions(
                ownerLabel, nodeId, HookPhase.ON_ERROR, hooks.getOnError(), workflowHookImplementationIds, errors);
        validateNodePhaseActions(
                ownerLabel, nodeId, HookPhase.FINALLY, hooks.getOnFinally(), workflowHookImplementationIds, errors);
    }

    private static void validateNodePhaseActions(
            String ownerLabel,
            String nodeId,
            HookPhase phase,
            List<HookActionDefinition> actions,
            Set<String> workflowHookImplementationIds,
            List<String> errors) {
        for (int i = 0; i < actions.size(); i++) {
            HookActionDefinition action = actions.get(i);
            String label = ownerLabel + " node '" + nodeId + "' hooks." + phaseJsonName(phase) + "[" + i + "]";
            if (action == null) {
                errors.add(label + ": action must not be null");
                continue;
            }
            if (isBlank(action.getImplementationId())) {
                errors.add(label + ": implementationId is required");
                continue;
            }
            if (!workflowHookImplementationIds.isEmpty()
                    && !workflowHookImplementationIds.contains(action.getImplementationId())) {
                errors.add(
                        label
                                + ": implementationId '"
                                + action.getImplementationId()
                                + "' is not registered on any workflow-level hook");
            }
        }
    }

    private static String phaseJsonName(HookPhase phase) {
        return switch (phase) {
            case PRE -> "pre";
            case ON_ERROR -> "onError";
            case FINALLY -> "finally";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
