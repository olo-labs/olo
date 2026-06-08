package org.olo.definition.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Cross-cutting hook applied to nodes matching a {@code pattern} (e.g. {@code analysis.*}, {@code **}).
 * <p>
 * Phases: {@link HookPhase#PRE}, {@link HookPhase#ON_ERROR}, {@link HookPhase#FINALLY}.
 */
@JsonDeserialize(builder = HookDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HookDefinition {

    private final String id;
    private final String pattern;
    private final HookActionDefinition pre;
    private final HookActionDefinition onError;
    private final HookActionDefinition onFinally;

    private HookDefinition(Builder builder) {
        this.id = builder.id;
        this.pattern = builder.pattern;
        this.pre = builder.pre;
        this.onError = builder.onError;
        this.onFinally = builder.onFinally;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    /**
     * Node id glob pattern ({@code **} = all nodes, {@code analysis.*} = prefix match).
     */
    public String getPattern() {
        return pattern;
    }

    public HookActionDefinition getPre() {
        return pre;
    }

    public HookActionDefinition getOnError() {
        return onError;
    }

    @JsonProperty("finally")
    public HookActionDefinition getOnFinally() {
        return onFinally;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HookDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(pattern, that.pattern)
                && Objects.equals(pre, that.pre)
                && Objects.equals(onError, that.onError)
                && Objects.equals(onFinally, that.onFinally);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, pattern, pre, onError, onFinally);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String pattern;
        private HookActionDefinition pre;
        private HookActionDefinition onError;
        private HookActionDefinition onFinally;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder pre(HookActionDefinition pre) {
            this.pre = pre;
            return this;
        }

        public Builder onError(HookActionDefinition onError) {
            this.onError = onError;
            return this;
        }

        @JsonProperty("finally")
        public Builder onFinally(HookActionDefinition onFinally) {
            this.onFinally = onFinally;
            return this;
        }

        public HookDefinition build() {
            Objects.requireNonNull(id, "hook id is required");
            Objects.requireNonNull(pattern, "hook pattern is required");
            return new HookDefinition(this);
        }
    }
}
