/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.planner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.olo.definition.capability.CapabilityDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Rich planner-facing view of a workflow, agent, tool, or node capability.
 * Includes operational hints and binding requirements; never exposes graph structure
 * ({@code nodes}, {@code edges}) or runtime {@code configuration}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PlannerCapability {

    private final String id;
    private final CatalogKind kind;
    private final String name;
    private final String description;
    private final List<String> requiredInputs;
    private final List<String> requiredOutputs;
    private final List<String> tags;
    private final List<String> examples;
    private final Double cost;
    private final Double latency;
    private final Double confidence;
    private final List<String> toolRequirements;
    private final List<String> requiredContext;

    public PlannerCapability(String id, CatalogKind kind, CapabilityDefinition capability) {
        Objects.requireNonNull(capability, "capability is required");
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind is required");
        this.name = capability.getName();
        this.description = capability.getDescription();
        this.requiredInputs = emptyToNull(capability.getRequiredInputs());
        this.requiredOutputs = emptyToNull(capability.getRequiredOutputs());
        this.tags = emptyToNull(capability.getTags());
        this.examples = emptyToNull(capability.getExamples());
        this.cost = capability.getCost();
        this.latency = capability.getLatency();
        this.confidence = capability.getConfidence();
        this.toolRequirements = emptyToNull(capability.getToolRequirements());
        this.requiredContext = emptyToNull(capability.getRequiredContext());
    }

    public String getId() {
        return id;
    }

    public CatalogKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @JsonProperty("required_inputs")
    public List<String> getRequiredInputs() {
        return requiredInputs == null ? List.of() : requiredInputs;
    }

    @JsonProperty("required_outputs")
    public List<String> getRequiredOutputs() {
        return requiredOutputs == null ? List.of() : requiredOutputs;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : tags;
    }

    public List<String> getExamples() {
        return examples == null ? List.of() : examples;
    }

    public Double getCost() {
        return cost;
    }

    public Double getLatency() {
        return latency;
    }

    public Double getConfidence() {
        return confidence;
    }

    @JsonProperty("tool_requirements")
    public List<String> getToolRequirements() {
        return toolRequirements == null ? List.of() : toolRequirements;
    }

    @JsonProperty("required_context")
    public List<String> getRequiredContext() {
        return requiredContext == null ? List.of() : requiredContext;
    }

    private static List<String> emptyToNull(List<String> values) {
        return values == null || values.isEmpty() ? null : List.copyOf(values);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlannerCapability that)) {
            return false;
        }
        return kind == that.kind
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(requiredInputs, that.requiredInputs)
                && Objects.equals(requiredOutputs, that.requiredOutputs)
                && Objects.equals(tags, that.tags)
                && Objects.equals(examples, that.examples)
                && Objects.equals(cost, that.cost)
                && Objects.equals(latency, that.latency)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(toolRequirements, that.toolRequirements)
                && Objects.equals(requiredContext, that.requiredContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                kind,
                name,
                description,
                requiredInputs,
                requiredOutputs,
                tags,
                examples,
                cost,
                latency,
                confidence,
                toolRequirements,
                requiredContext);
    }
}
