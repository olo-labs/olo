/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.runtime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.orchestration.MemoryScope;
import org.olo.definition.orchestration.ResultAggregation;
import org.olo.definition.orchestration.WorkflowOrchestrationDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Agent delegation policy nested under {@link WorkflowRuntimeDefinition#delegation}.
 * Keeps execution scheduling ({@code executionModel}) and delegation guardrails in one {@code runtime} block.
 */
@JsonDeserialize(builder = RuntimeDelegationDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
    "enabled",
    "parallelEnabled",
    "maxDepth",
    "maxDelegations",
    "resultAggregation",
    "memoryScope"
})
public final class RuntimeDelegationDefinition {

    private final Boolean enabled;
    private final Boolean parallelEnabled;
    private final Integer maxDepth;
    private final Integer maxDelegations;
    private final ResultAggregation resultAggregation;
    private final MemoryScope memoryScope;

    private RuntimeDelegationDefinition(Builder builder) {
        this.enabled = builder.enabled;
        this.parallelEnabled = builder.parallelEnabled;
        this.maxDepth = builder.maxDepth;
        this.maxDelegations = builder.maxDelegations;
        this.resultAggregation = builder.resultAggregation;
        this.memoryScope = builder.memoryScope;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Boolean getParallelEnabled() {
        return parallelEnabled;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public Integer getMaxDelegations() {
        return maxDelegations;
    }

    public ResultAggregation getResultAggregation() {
        return resultAggregation;
    }

    public MemoryScope getMemoryScope() {
        return memoryScope;
    }

    public static void validate(RuntimeDelegationDefinition delegation, String context, List<String> errors) {
        if (delegation == null) {
            return;
        }
        if (delegation.getMemoryScope() == null) {
            errors.add(context + ": runtime.delegation.memoryScope is required when delegation is set");
        }
        if (Boolean.TRUE.equals(delegation.getEnabled()) && delegation.getMaxDepth() == null) {
            errors.add(context + ": runtime.delegation.maxDepth is required when delegation.enabled is true");
        }
        if (Boolean.TRUE.equals(delegation.getEnabled()) && delegation.getMaxDelegations() == null) {
            errors.add(context + ": runtime.delegation.maxDelegations is required when delegation.enabled is true");
        }
        if (Boolean.TRUE.equals(delegation.getEnabled()) && delegation.getResultAggregation() == null) {
            errors.add(context + ": runtime.delegation.resultAggregation is required when delegation.enabled is true");
        }
        if (Boolean.TRUE.equals(delegation.getParallelEnabled())
                && !Boolean.TRUE.equals(delegation.getEnabled())) {
            errors.add(context + ": runtime.delegation.parallelEnabled requires delegation.enabled to be true");
        }
        if (delegation.getMaxDepth() != null && delegation.getMaxDepth() < 1) {
            errors.add(context + ": runtime.delegation.maxDepth must be at least 1");
        }
        if (delegation.getMaxDelegations() != null && delegation.getMaxDelegations() < 1) {
            errors.add(context + ": runtime.delegation.maxDelegations must be at least 1");
        }
    }

    /** Maps a legacy root-level {@code orchestration} block into {@code runtime.delegation}. */
    public static RuntimeDelegationDefinition fromLegacy(WorkflowOrchestrationDefinition legacy) {
        if (legacy == null) {
            return null;
        }
        return builder()
                .enabled(legacy.getAllowDelegation())
                .parallelEnabled(legacy.getAllowParallelDelegation())
                .maxDepth(legacy.getMaxDelegationDepth())
                .maxDelegations(legacy.getMaxDelegations())
                .resultAggregation(legacy.getResultAggregation())
                .memoryScope(legacy.getMemoryScope())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RuntimeDelegationDefinition that)) {
            return false;
        }
        return Objects.equals(enabled, that.enabled)
                && Objects.equals(parallelEnabled, that.parallelEnabled)
                && Objects.equals(maxDepth, that.maxDepth)
                && Objects.equals(maxDelegations, that.maxDelegations)
                && resultAggregation == that.resultAggregation
                && memoryScope == that.memoryScope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, parallelEnabled, maxDepth, maxDelegations, resultAggregation, memoryScope);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Boolean enabled;
        private Boolean parallelEnabled;
        private Integer maxDepth;
        private Integer maxDelegations;
        private ResultAggregation resultAggregation;
        private MemoryScope memoryScope;

        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder parallelEnabled(Boolean parallelEnabled) {
            this.parallelEnabled = parallelEnabled;
            return this;
        }

        public Builder maxDepth(Integer maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        @JsonAlias("maxDelegationDepth")
        public Builder maxDelegationDepth(Integer maxDepth) {
            return maxDepth(maxDepth);
        }

        public Builder maxDelegations(Integer maxDelegations) {
            this.maxDelegations = maxDelegations;
            return this;
        }

        public Builder resultAggregation(ResultAggregation resultAggregation) {
            this.resultAggregation = resultAggregation;
            return this;
        }

        public Builder memoryScope(MemoryScope memoryScope) {
            this.memoryScope = memoryScope;
            return this;
        }

        public RuntimeDelegationDefinition build() {
            return new RuntimeDelegationDefinition(this);
        }
    }
}
