/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.parameter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Studio validation hints for a workflow parameter ({@code parameters.*.validation}).
 */
@JsonDeserialize(builder = ParameterValidationDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ParameterValidationDefinition {

    private final Integer minLength;
    private final Integer maxLength;
    private final Double minimum;
    private final Double maximum;
    private final Double step;

    private ParameterValidationDefinition(Builder builder) {
        this.minLength = builder.minLength;
        this.maxLength = builder.maxLength;
        this.minimum = builder.minimum;
        this.maximum = builder.maximum;
        this.step = builder.step;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    public Double getStep() {
        return step;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParameterValidationDefinition that)) {
            return false;
        }
        return Objects.equals(minLength, that.minLength)
                && Objects.equals(maxLength, that.maxLength)
                && Objects.equals(minimum, that.minimum)
                && Objects.equals(maximum, that.maximum)
                && Objects.equals(step, that.step);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minLength, maxLength, minimum, maximum, step);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Integer minLength;
        private Integer maxLength;
        private Double minimum;
        private Double maximum;
        private Double step;

        public Builder minLength(Integer minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder minimum(Double minimum) {
            this.minimum = minimum;
            return this;
        }

        public Builder maximum(Double maximum) {
            this.maximum = maximum;
            return this;
        }

        public Builder step(Double step) {
            this.step = step;
            return this;
        }

        public ParameterValidationDefinition build() {
            if (minLength == null && maxLength == null && minimum == null && maximum == null && step == null) {
                return null;
            }
            return new ParameterValidationDefinition(this);
        }
    }
}
