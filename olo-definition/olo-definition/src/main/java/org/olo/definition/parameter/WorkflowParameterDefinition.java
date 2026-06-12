package org.olo.definition.parameter;



import com.fasterxml.jackson.annotation.JsonAlias;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;



import java.util.Map;

import java.util.Objects;



/**

 * Workflow tuning parameter ({@code parameters} on {@link org.olo.definition.workflow.WorkflowDefinition})

 * with optional Studio UI metadata. Runtime consumers use {@link #getType()}, {@link #getDefaultValue()},

 * and bounds; editors also read {@link #getUi()} and presentation fields.

 */

@JsonDeserialize(using = WorkflowParameterDefinitionDeserializer.class)

@JsonInclude(JsonInclude.Include.NON_NULL)

@JsonIgnoreProperties(ignoreUnknown = true)

@JsonPropertyOrder({

    "type",

    "label",

    "description",

    "defaultValue",

    "required",

    "validation",

    "visibleWhen",

    "ui"

})

public final class WorkflowParameterDefinition {



    private final String type;

    private final String label;

    private final String description;

    private final Object defaultValue;

    private final Boolean required;

    private final ParameterValidationDefinition validation;

    private final Map<String, String> visibleWhen;

    private final ParameterUiDefinition ui;



    private WorkflowParameterDefinition(Builder builder) {

        this.type = builder.type;

        this.label = builder.label;

        this.description = builder.description;

        this.defaultValue = builder.defaultValue;

        this.required = builder.required;

        this.validation = builder.validation;

        this.visibleWhen = builder.visibleWhen;

        this.ui = builder.ui;

    }



    public static Builder builder() {

        return new Builder();

    }



    @JsonProperty("type")

    public String getType() {

        return type;

    }



    /** @deprecated use {@link #getType()} — legacy presets used {@code schema}. */

    @Deprecated

    @JsonIgnore

    public String getSchema() {

        return type;

    }



    public String getLabel() {

        return label;

    }



    public String getDescription() {

        return description;

    }



    public Object getDefaultValue() {

        return defaultValue;

    }



    public Boolean getRequired() {

        return required;

    }



    public ParameterValidationDefinition getValidation() {

        return validation;

    }



    /** @deprecated use {@link #getValidation()} {@code .getMinimum()}. */

    @Deprecated

    @JsonIgnore

    public Double getMinimum() {

        return validation == null ? null : validation.getMinimum();

    }



    /** @deprecated use {@link #getValidation()} {@code .getMaximum()}. */

    @Deprecated

    @JsonIgnore

    public Double getMaximum() {

        return validation == null ? null : validation.getMaximum();

    }



    /** @deprecated use {@link #getValidation()} {@code .getStep()}. */

    @Deprecated

    @JsonIgnore

    public Double getStep() {

        return validation == null ? null : validation.getStep();

    }



    public Map<String, String> getVisibleWhen() {

        return visibleWhen;

    }



    public ParameterUiDefinition getUi() {

        return ui;

    }



    @Override

    public boolean equals(Object o) {

        if (this == o) {

            return true;

        }

        if (!(o instanceof WorkflowParameterDefinition that)) {

            return false;

        }

        return Objects.equals(type, that.type)

                && Objects.equals(label, that.label)

                && Objects.equals(description, that.description)

                && Objects.equals(defaultValue, that.defaultValue)

                && Objects.equals(required, that.required)

                && Objects.equals(validation, that.validation)

                && Objects.equals(visibleWhen, that.visibleWhen)

                && Objects.equals(ui, that.ui);

    }



    @Override

    public int hashCode() {

        return Objects.hash(type, label, description, defaultValue, required, validation, visibleWhen, ui);

    }



    @JsonPOJOBuilder(withPrefix = "")

    public static final class Builder {



        private String type;

        private String label;

        private String description;

        private Object defaultValue;

        private Boolean required;

        private ParameterValidationDefinition validation;

        private Map<String, String> visibleWhen;

        private ParameterUiDefinition ui;



        public Builder type(String type) {

            this.type = type;

            return this;

        }



        @JsonAlias("schema")

        public Builder schema(String schema) {

            return type(schema);

        }



        public Builder label(String label) {

            this.label = label;

            return this;

        }



        public Builder description(String description) {

            this.description = description;

            return this;

        }



        public Builder defaultValue(Object defaultValue) {

            this.defaultValue = defaultValue;

            return this;

        }



        public Builder required(Boolean required) {

            this.required = required;

            return this;

        }



        public Builder validation(ParameterValidationDefinition validation) {

            this.validation = validation;

            return this;

        }



        public Builder minimum(Double minimum) {

            validation(mergeValidation().minimum(minimum).build());

            return this;

        }



        public Builder maximum(Double maximum) {

            validation(mergeValidation().maximum(maximum).build());

            return this;

        }



        public Builder step(Double step) {

            validation(mergeValidation().step(step).build());

            return this;

        }



        public Builder minLength(Integer minLength) {

            validation(mergeValidation().minLength(minLength).build());

            return this;

        }



        public Builder maxLength(Integer maxLength) {

            validation(mergeValidation().maxLength(maxLength).build());

            return this;

        }



        public Builder visibleWhen(Map<String, String> visibleWhen) {

            this.visibleWhen = visibleWhen;

            return this;

        }



        public Builder ui(ParameterUiDefinition ui) {

            this.ui = ui;

            return this;

        }



        public WorkflowParameterDefinition build() {

            Objects.requireNonNull(type, "parameter type is required");

            return new WorkflowParameterDefinition(this);

        }



        private ParameterValidationDefinition.Builder mergeValidation() {

            ParameterValidationDefinition.Builder builder = ParameterValidationDefinition.builder();

            if (validation != null) {

                builder.minLength(validation.getMinLength())

                        .maxLength(validation.getMaxLength())

                        .minimum(validation.getMinimum())

                        .maximum(validation.getMaximum())

                        .step(validation.getStep());

            }

            return builder;

        }

    }

}


