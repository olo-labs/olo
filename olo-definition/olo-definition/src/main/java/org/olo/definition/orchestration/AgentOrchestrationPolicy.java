package org.olo.definition.orchestration;

import org.olo.definition.runtime.AgentDelegationPolicy;
import org.olo.definition.runtime.RuntimeDelegationDefinition;

/**
 * @deprecated Use {@link AgentDelegationPolicy} ({@code runtime.delegation}).
 */
@Deprecated
public final class AgentOrchestrationPolicy {

    private AgentOrchestrationPolicy() {
    }

    @Deprecated
    public static WorkflowOrchestrationDefinition agentPresetDefaults() {
        RuntimeDelegationDefinition delegation = AgentDelegationPolicy.agentPresetDefaults();
        return WorkflowOrchestrationDefinition.builder()
                .allowDelegation(delegation.getEnabled())
                .allowParallelDelegation(delegation.getParallelEnabled())
                .maxDelegationDepth(delegation.getMaxDepth())
                .maxDelegations(delegation.getMaxDelegations())
                .resultAggregation(delegation.getResultAggregation())
                .memoryScope(delegation.getMemoryScope())
                .build();
    }
}
