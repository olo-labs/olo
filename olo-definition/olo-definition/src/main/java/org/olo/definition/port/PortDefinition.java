package org.olo.definition.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Declares a typed connection point on a node. Edges reference ports by {@link #getId()}.
 */
@JsonDeserialize(builder = PortDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class PortDefinition {

    private final String id;
    private final String name;
    private final String schema;
    private final PortDirection direction;
    private final boolean required;
    private final int minConnections;
    private final Integer maxConnections;
    private final PortUiDefinition ui;

    private PortDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.schema = builder.schema;
        this.direction = builder.direction;
        this.required = builder.required;
        this.minConnections = builder.minConnections;
        this.maxConnections = builder.maxConnections;
        this.ui = builder.ui == null ? PortUiDefinition.forDirection(builder.direction) : builder.ui;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PortDefinition inputPort(String id, String schema) {
        return builder().id(id).name(id).schema(schema).direction(PortDirection.INPUT).build();
    }

    public static PortDefinition outputPort(String id, String schema) {
        return builder().id(id).name(id).schema(schema).direction(PortDirection.OUTPUT).build();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSchema() {
        return schema;
    }

    public PortDirection getDirection() {
        return direction;
    }

    public boolean isRequired() {
        return required;
    }

    public int getMinConnections() {
        return minConnections;
    }

    /** {@code null} means no upper bound. */
    public Integer getMaxConnections() {
        return maxConnections;
    }

    public PortUiDefinition getUi() {
        return ui;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PortDefinition that)) {
            return false;
        }
        return required == that.required
                && minConnections == that.minConnections
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(schema, that.schema)
                && direction == that.direction
                && Objects.equals(maxConnections, that.maxConnections)
                && Objects.equals(ui, that.ui);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, schema, direction, required, minConnections, maxConnections, ui);
    }

    @Override
    public String toString() {
        return "PortDefinition{id='" + id + "', name='" + name + "', direction=" + direction + ", schema='" + schema + "'}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String schema;
        private PortDirection direction;
        private boolean required;
        private int minConnections;
        private Integer maxConnections;
        private PortUiDefinition ui;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(String schema) {
            this.schema = schema;
            return this;
        }

        public Builder direction(PortDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder minConnections(int minConnections) {
            this.minConnections = minConnections;
            return this;
        }

        public Builder maxConnections(Integer maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder ui(PortUiDefinition ui) {
            this.ui = ui;
            return this;
        }

        public PortDefinition build() {
            Objects.requireNonNull(name, "port name is required");
            Objects.requireNonNull(schema, "port schema is required");
            Objects.requireNonNull(direction, "port direction is required");
            if (id == null) {
                id = name;
            }
            if (minConnections < 0) {
                throw new IllegalArgumentException("minConnections must be >= 0");
            }
            if (maxConnections != null && maxConnections < minConnections) {
                throw new IllegalArgumentException("maxConnections must be >= minConnections");
            }
            return new PortDefinition(this);
        }
    }
}
