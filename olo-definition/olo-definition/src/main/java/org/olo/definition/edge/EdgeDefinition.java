package org.olo.definition.edge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Directed connection between two node ports in a workflow graph.
 */
@JsonDeserialize(builder = EdgeDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class EdgeDefinition {

    private final String sourceNodeId;
    private final String sourcePortId;
    private final String targetNodeId;
    private final String targetPortId;

    private EdgeDefinition(Builder builder) {
        this.sourceNodeId = builder.sourceNodeId;
        this.sourcePortId = builder.sourcePortId;
        this.targetNodeId = builder.targetNodeId;
        this.targetPortId = builder.targetPortId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getSourcePortId() {
        return sourcePortId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getTargetPortId() {
        return targetPortId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EdgeDefinition that)) {
            return false;
        }
        return Objects.equals(sourceNodeId, that.sourceNodeId)
                && Objects.equals(sourcePortId, that.sourcePortId)
                && Objects.equals(targetNodeId, that.targetNodeId)
                && Objects.equals(targetPortId, that.targetPortId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNodeId, sourcePortId, targetNodeId, targetPortId);
    }

    @Override
    public String toString() {
        return "EdgeDefinition{"
                + sourceNodeId
                + (sourcePortId != null ? ":" + sourcePortId : "")
                + " -> "
                + targetNodeId
                + (targetPortId != null ? ":" + targetPortId : "")
                + "}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String sourceNodeId;
        private String sourcePortId;
        private String targetNodeId;
        private String targetPortId;

        public Builder sourceNodeId(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
            return this;
        }

        public Builder sourcePortId(String sourcePortId) {
            this.sourcePortId = sourcePortId;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder targetPortId(String targetPortId) {
            this.targetPortId = targetPortId;
            return this;
        }

        public EdgeDefinition build() {
            Objects.requireNonNull(sourceNodeId, "sourceNodeId is required");
            Objects.requireNonNull(targetNodeId, "targetNodeId is required");
            return new EdgeDefinition(this);
        }
    }
}
