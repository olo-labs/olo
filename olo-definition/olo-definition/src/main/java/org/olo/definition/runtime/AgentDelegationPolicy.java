package org.olo.definition.runtime;

import org.olo.definition.orchestration.MemoryScope;
import org.olo.definition.orchestration.ResultAggregation;

/**
 * Default {@code runtime.delegation} policy for the agent workflow preset.
 */
public final class AgentDelegationPolicy {

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_PARALLEL_ENABLED = true;
    public static final int DEFAULT_MAX_DEPTH = 3;
    public static final int DEFAULT_MAX_DELEGATIONS = 10;
    public static final ResultAggregation DEFAULT_RESULT_AGGREGATION = ResultAggregation.MERGE;
    public static final MemoryScope DEFAULT_MEMORY_SCOPE = MemoryScope.SHARED;

    private AgentDelegationPolicy() {
    }

    public static RuntimeDelegationDefinition agentPresetDefaults() {
        return RuntimeDelegationDefinition.builder()
                .enabled(DEFAULT_ENABLED)
                .parallelEnabled(DEFAULT_PARALLEL_ENABLED)
                .maxDepth(DEFAULT_MAX_DEPTH)
                .maxDelegations(DEFAULT_MAX_DELEGATIONS)
                .resultAggregation(DEFAULT_RESULT_AGGREGATION)
                .memoryScope(DEFAULT_MEMORY_SCOPE)
                .build();
    }
}
