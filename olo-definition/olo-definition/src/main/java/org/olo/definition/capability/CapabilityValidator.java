/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.capability;

import java.util.List;

/**
 * Structural validation for {@link CapabilityDefinition} blocks.
 */
public final class CapabilityValidator {

    public enum Context {
        /** Workflow root artifact — requires non-empty inputs and outputs. */
        WORKFLOW,
        /** Tool registry entry — name and description only. */
        TOOL,
        /** Agent registry entry — name and description only. */
        AGENT,
        /** Optional node-level planner hint. */
        NODE
    }

    private CapabilityValidator() {
    }

    public static void validate(
            String ownerId, CapabilityDefinition capability, Context context, List<String> errors) {
        if (capability == null) {
            errors.add(ownerId + ": capability is required");
            return;
        }
        if (isBlank(capability.getName())) {
            errors.add(ownerId + ": capability name is required");
        }
        if (isBlank(capability.getDescription())) {
            errors.add(ownerId + ": capability description is required");
        }
        if (!isBlank(capability.getId()) && !isBlank(ownerId) && !capability.getId().equals(ownerId)) {
            errors.add(
                    ownerId + ": capability id '" + capability.getId() + "' must match owner id '" + ownerId + "'");
        }
        if (context == Context.WORKFLOW) {
            if (capability.getRequiredInputs().isEmpty()) {
                errors.add(ownerId + ": capability must declare at least one required input");
            }
            if (capability.getRequiredOutputs().isEmpty()) {
                errors.add(ownerId + ": capability must declare at least one required output");
            }
        }
        for (String input : capability.getRequiredInputs()) {
            if (isBlank(input)) {
                errors.add(ownerId + ": capability required input names must not be blank");
                break;
            }
        }
        for (String output : capability.getRequiredOutputs()) {
            if (isBlank(output)) {
                errors.add(ownerId + ": capability required output names must not be blank");
                break;
            }
        }
        Double confidence = capability.getConfidence();
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            errors.add(ownerId + ": capability confidence must be between 0.0 and 1.0");
        }
        if (capability.getCost() != null && capability.getCost() < 0.0) {
            errors.add(ownerId + ": capability cost must be >= 0");
        }
        if (capability.getLatency() != null && capability.getLatency() < 0.0) {
            errors.add(ownerId + ": capability latency must be >= 0");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
