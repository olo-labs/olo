/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.runtime;

/**
 * Resolves how an artifact should be executed from {@link RuntimeBindingDefinition}.
 * <p>
 * Order:
 * <pre>
 * implementationClass → implementationId → endpoint → default execution (workflowRef / WorkflowExecutor)
 * </pre>
 */
public final class RuntimeBindingResolver {

    private RuntimeBindingResolver() {
    }

    public static RuntimeBindingResolution resolve(RuntimeBindingDefinition binding) {
        if (binding == null) {
            return RuntimeBindingResolution.DEFAULT_EXECUTION;
        }
        if (!isBlank(binding.getImplementationClass())) {
            return RuntimeBindingResolution.IMPLEMENTATION_CLASS;
        }
        if (!isBlank(binding.getImplementationId())) {
            return RuntimeBindingResolution.IMPLEMENTATION_ID;
        }
        if (!isBlank(binding.getEndpoint())) {
            return RuntimeBindingResolution.ENDPOINT;
        }
        return RuntimeBindingResolution.DEFAULT_EXECUTION;
    }

    /**
     * Effective runtime kind when {@link RuntimeBindingResolution} is {@code IMPLEMENTATION_CLASS}.
     */
    public static String effectiveRuntime(RuntimeBindingDefinition binding) {
        if (binding == null || isBlank(binding.getRuntime())) {
            return RuntimeKind.JAVA;
        }
        return binding.getRuntime();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
