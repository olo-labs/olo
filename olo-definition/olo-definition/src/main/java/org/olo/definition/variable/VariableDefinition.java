package org.olo.definition.variable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Legacy workflow-scoped variable list. Prefer {@link org.olo.definition.input.WorkflowInputDefinition}
 * under {@code inputs} on {@link org.olo.definition.workflow.WorkflowDefinition}.
 *
 * @deprecated use {@code inputs} map with {@link org.olo.definition.input.WorkflowInputDefinition}
 */
@Deprecated
@JsonDeserialize(builder = VariableDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VariableDefinition {

    private final String name;
    private final String type;
    private final Object defaultValue;
    private final String description;
    private final boolean required;
    private final Map<String, Object> metadata;

    private VariableDefinition(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
        this.required = builder.required;
        this.metadata = builder.metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
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

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VariableDefinition that)) {
            return false;
        }
        return required == that.required
                && Objects.equals(name, that.name)
                && Objects.equals(type, that.type)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(description, that.description)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, defaultValue, description, required, metadata);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String name;
        private String type;
        private Object defaultValue;
        private String description;
        private boolean required;
        private Map<String, Object> metadata;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public VariableDefinition build() {
            Objects.requireNonNull(name, "variable name is required");
            return new VariableDefinition(this);
        }
    }
}
