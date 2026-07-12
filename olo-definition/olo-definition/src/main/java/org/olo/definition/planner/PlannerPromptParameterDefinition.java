/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * A named placeholder referenced in a {@link WorkflowPlannerPromptDefinition}.
 */
@JsonDeserialize(builder = PlannerPromptParameterDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PlannerPromptParameterDefinition {

    private final String name;
    private final String type;
    private final Boolean required;

    private PlannerPromptParameterDefinition(Builder builder) {
        this.name = builder.name;
        this.type = builder.type == null ? "string" : builder.type;
        this.required = builder.required;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Boolean getRequired() {
        return required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlannerPromptParameterDefinition that)) {
            return false;
        }
        return Objects.equals(name, that.name)
                && Objects.equals(type, that.type)
                && Objects.equals(required, that.required);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, required);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String name;
        private String type;
        private Boolean required;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder required(Boolean required) {
            this.required = required;
            return this;
        }

        public PlannerPromptParameterDefinition build() {
            Objects.requireNonNull(name, "planner prompt parameter name is required");
            return new PlannerPromptParameterDefinition(this);
        }
    }
}
