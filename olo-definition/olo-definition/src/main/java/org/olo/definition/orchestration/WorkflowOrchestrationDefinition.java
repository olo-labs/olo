/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.orchestration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Legacy delegation policy at the workflow root ({@code orchestration}).
 *
 * @deprecated Use {@link org.olo.definition.runtime.RuntimeDelegationDefinition} under
 *             {@code runtime.delegation}. Retained for deserializing legacy JSON.
 */
@Deprecated
@JsonDeserialize(builder = WorkflowOrchestrationDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
    "allowDelegation",
    "allowParallelDelegation",
    "maxDelegationDepth",
    "maxDelegations",
    "resultAggregation",
    "memoryScope"
})
public final class WorkflowOrchestrationDefinition {

    private final Boolean allowDelegation;
    private final Boolean allowParallelDelegation;
    private final Integer maxDelegationDepth;
    private final Integer maxDelegations;
    private final ResultAggregation resultAggregation;
    private final MemoryScope memoryScope;

    private WorkflowOrchestrationDefinition(Builder builder) {
        this.allowDelegation = builder.allowDelegation;
        this.allowParallelDelegation = builder.allowParallelDelegation;
        this.maxDelegationDepth = builder.maxDelegationDepth;
        this.maxDelegations = builder.maxDelegations;
        this.resultAggregation = builder.resultAggregation;
        this.memoryScope = builder.memoryScope;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Boolean getAllowDelegation() {
        return allowDelegation;
    }

    public Boolean getAllowParallelDelegation() {
        return allowParallelDelegation;
    }

    public Integer getMaxDelegationDepth() {
        return maxDelegationDepth;
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

    public static void validate(
            WorkflowOrchestrationDefinition orchestration, String context, List<String> errors) {
        if (orchestration == null) {
            return;
        }
        if (orchestration.getMemoryScope() == null) {
            errors.add(context + ": orchestration.memoryScope is required when orchestration is set");
        }
        if (Boolean.TRUE.equals(orchestration.getAllowDelegation())
                && orchestration.getMaxDelegationDepth() == null) {
            errors.add(context + ": orchestration.maxDelegationDepth is required when allowDelegation is true");
        }
        if (Boolean.TRUE.equals(orchestration.getAllowDelegation())
                && orchestration.getMaxDelegations() == null) {
            errors.add(context + ": orchestration.maxDelegations is required when allowDelegation is true");
        }
        if (Boolean.TRUE.equals(orchestration.getAllowDelegation())
                && orchestration.getResultAggregation() == null) {
            errors.add(context + ": orchestration.resultAggregation is required when allowDelegation is true");
        }
        if (Boolean.TRUE.equals(orchestration.getAllowParallelDelegation())
                && !Boolean.TRUE.equals(orchestration.getAllowDelegation())) {
            errors.add(context
                    + ": orchestration.allowParallelDelegation requires allowDelegation to be true");
        }
        if (orchestration.getMaxDelegationDepth() != null && orchestration.getMaxDelegationDepth() < 1) {
            errors.add(context + ": orchestration.maxDelegationDepth must be at least 1");
        }
        if (orchestration.getMaxDelegations() != null && orchestration.getMaxDelegations() < 1) {
            errors.add(context + ": orchestration.maxDelegations must be at least 1");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowOrchestrationDefinition that)) {
            return false;
        }
        return Objects.equals(allowDelegation, that.allowDelegation)
                && Objects.equals(allowParallelDelegation, that.allowParallelDelegation)
                && Objects.equals(maxDelegationDepth, that.maxDelegationDepth)
                && Objects.equals(maxDelegations, that.maxDelegations)
                && resultAggregation == that.resultAggregation
                && memoryScope == that.memoryScope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                allowDelegation,
                allowParallelDelegation,
                maxDelegationDepth,
                maxDelegations,
                resultAggregation,
                memoryScope);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Boolean allowDelegation;
        private Boolean allowParallelDelegation;
        private Integer maxDelegationDepth;
        private Integer maxDelegations;
        private ResultAggregation resultAggregation;
        private MemoryScope memoryScope;

        public Builder allowDelegation(Boolean allowDelegation) {
            this.allowDelegation = allowDelegation;
            return this;
        }

        public Builder allowParallelDelegation(Boolean allowParallelDelegation) {
            this.allowParallelDelegation = allowParallelDelegation;
            return this;
        }

        public Builder maxDelegationDepth(Integer maxDelegationDepth) {
            this.maxDelegationDepth = maxDelegationDepth;
            return this;
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

        public WorkflowOrchestrationDefinition build() {
            return new WorkflowOrchestrationDefinition(this);
        }
    }
}
