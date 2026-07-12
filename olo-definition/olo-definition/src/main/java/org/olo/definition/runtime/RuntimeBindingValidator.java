/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.runtime;

import java.util.List;

/**
 * Structural validation for {@link RuntimeBindingDefinition}.
 */
public final class RuntimeBindingValidator {

    private RuntimeBindingValidator() {
    }

    public static void validate(String ownerLabel, RuntimeBindingDefinition binding, List<String> errors) {
        if (binding == null) {
            return;
        }
        boolean hasClass = !isBlank(binding.getImplementationClass());
        boolean hasId = !isBlank(binding.getImplementationId());
        boolean hasEndpoint = !isBlank(binding.getEndpoint());

        if (!hasClass && !hasId && !hasEndpoint) {
            errors.add(
                    ownerLabel
                            + ": runtimeBinding must set implementationClass, implementationId, or endpoint");
            return;
        }

        String runtime = binding.getRuntime();
        if (!isBlank(runtime) && !RuntimeKind.isKnown(runtime)) {
            // Allow unknown values for forward compatibility; no error.
        }

        if (hasClass && !isBlank(runtime) && !RuntimeKind.JAVA.equals(runtime)) {
            errors.add(
                    ownerLabel
                            + ": implementationClass is only valid with runtime 'java' (or omitted)");
        }

        String effectiveRuntime = RuntimeBindingResolver.effectiveRuntime(binding);
        if (RuntimeKind.HTTP.equals(effectiveRuntime) || RuntimeKind.PYTHON.equals(runtime)) {
            if (!hasId && !hasEndpoint) {
                errors.add(
                        ownerLabel
                                + ": runtime '"
                                + runtime
                                + "' requires implementationId or endpoint");
            }
        }

        if (hasEndpoint && isBlank(runtime)) {
            errors.add(ownerLabel + ": endpoint requires runtime (typically 'http')");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
