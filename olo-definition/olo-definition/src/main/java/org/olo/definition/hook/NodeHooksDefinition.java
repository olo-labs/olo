package org.olo.definition.hook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Per-node hook bindings. Each phase lists {@link HookActionDefinition} entries chosen from
 * implementation ids registered on workflow-level {@link HookDefinition} hooks.
 */
@JsonDeserialize(builder = NodeHooksDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NodeHooksDefinition {

    private final List<HookActionDefinition> pre;
    private final List<HookActionDefinition> onError;
    private final List<HookActionDefinition> onFinally;

    private NodeHooksDefinition(Builder builder) {
        this.pre = builder.pre == null ? List.of() : List.copyOf(builder.pre);
        this.onError = builder.onError == null ? List.of() : List.copyOf(builder.onError);
        this.onFinally = builder.onFinally == null ? List.of() : List.copyOf(builder.onFinally);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<HookActionDefinition> getPre() {
        return pre;
    }

    public List<HookActionDefinition> getOnError() {
        return onError;
    }

    @JsonProperty("finally")
    public List<HookActionDefinition> getOnFinally() {
        return onFinally;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeHooksDefinition that)) {
            return false;
        }
        return Objects.equals(pre, that.pre)
                && Objects.equals(onError, that.onError)
                && Objects.equals(onFinally, that.onFinally);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pre, onError, onFinally);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private List<HookActionDefinition> pre;
        private List<HookActionDefinition> onError;
        private List<HookActionDefinition> onFinally;

        public Builder pre(List<HookActionDefinition> pre) {
            this.pre = pre;
            return this;
        }

        public Builder addPre(HookActionDefinition action) {
            if (this.pre == null) {
                this.pre = new java.util.ArrayList<>();
            }
            this.pre.add(action);
            return this;
        }

        public Builder onError(List<HookActionDefinition> onError) {
            this.onError = onError;
            return this;
        }

        public Builder addOnError(HookActionDefinition action) {
            if (this.onError == null) {
                this.onError = new java.util.ArrayList<>();
            }
            this.onError.add(action);
            return this;
        }

        @JsonProperty("finally")
        public Builder onFinally(List<HookActionDefinition> onFinally) {
            this.onFinally = onFinally;
            return this;
        }

        public Builder addOnFinally(HookActionDefinition action) {
            if (this.onFinally == null) {
                this.onFinally = new java.util.ArrayList<>();
            }
            this.onFinally.add(action);
            return this;
        }

        public NodeHooksDefinition build() {
            return new NodeHooksDefinition(this);
        }
    }
}
