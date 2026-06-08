package org.olo.definition.input;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Workflow invocation input declared under {@code inputs} on {@link org.olo.definition.workflow.WorkflowDefinition}.
 * Map key is the input name (e.g. {@code symbol: INFY} at runtime).
 * <p>
 * By default {@link #isPopulateState()} is {@code true}: at workflow start the runtime copies
 * {@code input.{name}} into {@code state.{name}} unless disabled.
 */
@JsonDeserialize(builder = WorkflowInputDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WorkflowInputDefinition {

    private final String schema;
    private final Object defaultValue;
    private final String description;
    private final boolean required;
    private final boolean populateState;

    private WorkflowInputDefinition(Builder builder) {
        this.schema = builder.schema;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
        this.required = builder.required;
        this.populateState = builder.populateState;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSchema() {
        return schema;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * When {@code true} (default), runtime initializes {@code state.{name}} from {@code input.{name}} at workflow start.
     */
    public boolean isPopulateState() {
        return populateState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowInputDefinition that)) {
            return false;
        }
        return required == that.required
                && populateState == that.populateState
                && Objects.equals(schema, that.schema)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, defaultValue, description, required, populateState);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String schema;
        private Object defaultValue;
        private String description;
        private boolean required;
        private boolean populateState = true;

        @JsonAlias("type")
        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * When {@code false}, {@code input.{name}} is not copied into {@code state.{name}} at workflow start.
         */
        public Builder populateState(boolean populateState) {
            this.populateState = populateState;
            return this;
        }

        public WorkflowInputDefinition build() {
            Objects.requireNonNull(schema, "input schema is required");
            return new WorkflowInputDefinition(this);
        }
    }
}
