package org.olo.definition.parameter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Runtime tuning parameter for a workflow (e.g. {@code temperature: 0.2}), declared under
 * {@code parameters} on {@link org.olo.definition.workflow.WorkflowDefinition}. Not the same as
 * workflow {@linkplain org.olo.definition.state.StateFieldDefinition state} or node {@code configuration}.
 */
@JsonDeserialize(builder = WorkflowParameterDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WorkflowParameterDefinition {

    private final String schema;
    private final Object defaultValue;
    private final String description;

    private WorkflowParameterDefinition(Builder builder) {
        this.schema = builder.schema;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowParameterDefinition that)) {
            return false;
        }
        return Objects.equals(schema, that.schema)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, defaultValue, description);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String schema;
        private Object defaultValue;
        private String description;

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

        public WorkflowParameterDefinition build() {
            Objects.requireNonNull(schema, "parameter schema is required");
            return new WorkflowParameterDefinition(this);
        }
    }
}
