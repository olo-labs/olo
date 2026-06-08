package org.olo.definition.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Runtime binding for a single hook phase (resolved via {@code implementationId} in the registry).
 */
@JsonDeserialize(builder = HookActionDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HookActionDefinition {

    private final String implementationId;

    private HookActionDefinition(Builder builder) {
        this.implementationId = builder.implementationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getImplementationId() {
        return implementationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HookActionDefinition that)) {
            return false;
        }
        return Objects.equals(implementationId, that.implementationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(implementationId);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String implementationId;

        public Builder implementationId(String implementationId) {
            this.implementationId = implementationId;
            return this;
        }

        public HookActionDefinition build() {
            return new HookActionDefinition(this);
        }
    }
}
