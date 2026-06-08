package org.olo.definition.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Declares one field in workflow {@linkplain org.olo.definition.workflow.WorkflowDefinition#getState() state}.
 * The map key is the field name (e.g. {@code symbol}, {@code analysis}).
 */
@JsonDeserialize(builder = StateFieldDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class StateFieldDefinition {

    private final String schema;
    private final String description;

    private StateFieldDefinition(Builder builder) {
        this.schema = builder.schema;
        this.description = builder.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSchema() {
        return schema;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StateFieldDefinition that)) {
            return false;
        }
        return Objects.equals(schema, that.schema) && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, description);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String schema;
        private String description;

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public StateFieldDefinition build() {
            Objects.requireNonNull(schema, "state field schema is required");
            return new StateFieldDefinition(this);
        }
    }
}
