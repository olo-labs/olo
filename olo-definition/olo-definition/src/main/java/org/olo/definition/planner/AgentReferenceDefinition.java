package org.olo.definition.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Reference to an agent workflow the runtime may delegate to ({@code availableAgents[]} entry).
 * <p>
 * Shorthand id strings deserialize for backward compatibility; object form allows future metadata
 * (marketplace version pins, labels, capability filters).
 */
@JsonDeserialize(using = AgentReferenceDefinitionDeserializer.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AgentReferenceDefinition {

    private final String id;

    private AgentReferenceDefinition(Builder builder) {
        this.id = builder.id;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AgentReferenceDefinition of(String id) {
        return builder().id(id).build();
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentReferenceDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AgentReferenceDefinition{id='" + id + "'}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public AgentReferenceDefinition build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("availableAgents id is required");
            }
            return new AgentReferenceDefinition(this);
        }
    }
}
