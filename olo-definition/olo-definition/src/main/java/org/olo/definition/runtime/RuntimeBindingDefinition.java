package org.olo.definition.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Runtime execution binding for workflows, agents, tools, and nodes.
 * <p>
 * Resolution order (see {@link RuntimeBindingResolver}):
 * <ol>
 *   <li>{@code implementationClass} — custom class (JVM when {@code runtime} is {@code java} or unset)</li>
 *   <li>{@code implementationId} — registry lookup ({@code default-agent-runner}, {@code stock-screener}, …)</li>
 *   <li>{@code endpoint} — remote service (typical with {@code runtime: http})</li>
 *   <li>default — standard {@code WorkflowExecutor} / child workflow from {@code workflow} / {@code workflowRef}</li>
 * </ol>
 */
@JsonDeserialize(builder = RuntimeBindingDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RuntimeBindingDefinition {

    private final String implementationId;
    private final String implementationClass;
    private final String provider;
    private final String runtime;
    private final String endpoint;

    private RuntimeBindingDefinition(Builder builder) {
        this.implementationId = builder.implementationId;
        this.implementationClass = builder.implementationClass;
        this.provider = builder.provider;
        this.runtime = builder.runtime;
        this.endpoint = builder.endpoint;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getImplementationId() {
        return implementationId;
    }

    public String getImplementationClass() {
        return implementationClass;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Execution environment: {@code java}, {@code python}, {@code http}, etc.
     * Defaults to {@link RuntimeKind#JAVA} when {@code implementationClass} is set and {@code runtime} is absent.
     */
    public String getRuntime() {
        return runtime;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RuntimeBindingDefinition that)) {
            return false;
        }
        return Objects.equals(implementationId, that.implementationId)
                && Objects.equals(implementationClass, that.implementationClass)
                && Objects.equals(provider, that.provider)
                && Objects.equals(runtime, that.runtime)
                && Objects.equals(endpoint, that.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(implementationId, implementationClass, provider, runtime, endpoint);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String implementationId;
        private String implementationClass;
        private String provider;
        private String runtime;
        private String endpoint;

        public Builder implementationId(String implementationId) {
            this.implementationId = implementationId;
            return this;
        }

        public Builder implementationClass(String implementationClass) {
            this.implementationClass = implementationClass;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public RuntimeBindingDefinition build() {
            return new RuntimeBindingDefinition(this);
        }
    }
}
