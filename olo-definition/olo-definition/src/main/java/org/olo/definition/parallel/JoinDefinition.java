package org.olo.definition.parallel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Join semantics after a {@code PARALLEL} fork. Required on {@code PARALLEL} nodes so runtime
 * behavior is unambiguous (Temporal-aligned).
 */
@JsonDeserialize(builder = JoinDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JoinDefinition {

    private final JoinStrategy strategy;
    private final Integer quorumCount;

    private JoinDefinition(Builder builder) {
        this.strategy = builder.strategy;
        this.quorumCount = builder.quorumCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public JoinStrategy getStrategy() {
        return strategy;
    }

    public Integer getQuorumCount() {
        return quorumCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JoinDefinition that)) {
            return false;
        }
        return strategy == that.strategy && Objects.equals(quorumCount, that.quorumCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy, quorumCount);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private JoinStrategy strategy;
        private Integer quorumCount;

        public Builder strategy(JoinStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public Builder quorumCount(Integer quorumCount) {
            this.quorumCount = quorumCount;
            return this;
        }

        public JoinDefinition build() {
            Objects.requireNonNull(strategy, "join strategy is required");
            return new JoinDefinition(this);
        }
    }
}
