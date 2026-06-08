package org.olo.definition.runtime;

/**
 * Result of resolving a {@link RuntimeBindingDefinition} (definition-time only; actual dispatch is in {@code olo-runtime}).
 */
public enum RuntimeBindingResolution {
    /** Load {@code implementationClass} (JVM or declared {@code runtime}). */
    IMPLEMENTATION_CLASS,
    /** Look up {@code implementationId} in the runtime registry. */
    IMPLEMENTATION_ID,
    /** Invoke {@code endpoint} (remote HTTP/gRPC service). */
    ENDPOINT,
    /** Standard engine: {@code WorkflowExecutor}, child workflow from {@code workflow}/{@code workflowRef}, default tool runner. */
    DEFAULT_EXECUTION
}
