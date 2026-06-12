package org.olo.definition.designer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Canvas rendering size hint ({@code designer.nodeSize}).
 */
@JsonDeserialize(builder = NodeSizeDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NodeSizeDefinition {

    private final Integer width;
    private final Integer height;

    private NodeSizeDefinition(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeSizeDefinition that)) {
            return false;
        }
        return Objects.equals(width, that.width) && Objects.equals(height, that.height);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private Integer width;
        private Integer height;

        public Builder width(Integer width) {
            this.width = width;
            return this;
        }

        public Builder height(Integer height) {
            this.height = height;
            return this;
        }

        public NodeSizeDefinition build() {
            return new NodeSizeDefinition(this);
        }
    }
}
