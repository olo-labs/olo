package org.olo.definition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routing rules that select a {@link ModelProviderDefinition} for a node or request context.
 */
@JsonDeserialize(builder = ModelRoutingDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ModelRoutingDefinition {

    private final String id;
    private final String defaultProviderId;
    private final List<RoutingRule> rules;
    private final Map<String, Object> metadata;

    private ModelRoutingDefinition(Builder builder) {
        this.id = builder.id;
        this.defaultProviderId = builder.defaultProviderId;
        this.rules = builder.rules == null
                ? List.of()
                : List.copyOf(builder.rules);
        this.metadata = builder.metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getDefaultProviderId() {
        return defaultProviderId;
    }

    public List<RoutingRule> getRules() {
        return rules;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelRoutingDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(defaultProviderId, that.defaultProviderId)
                && Objects.equals(rules, that.rules)
                && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, defaultProviderId, rules, metadata);
    }

    @JsonDeserialize(builder = RoutingRule.Builder.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RoutingRule {

        private final String name;
        private final String providerId;
        private final Map<String, Object> match;

        private RoutingRule(Builder builder) {
            this.name = builder.name;
            this.providerId = builder.providerId;
            this.match = builder.match == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(builder.match));
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getName() {
            return name;
        }

        public String getProviderId() {
            return providerId;
        }

        public Map<String, Object> getMatch() {
            return match;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof RoutingRule that)) {
                return false;
            }
            return Objects.equals(name, that.name)
                    && Objects.equals(providerId, that.providerId)
                    && Objects.equals(match, that.match);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, providerId, match);
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {

            private String name;
            private String providerId;
            private Map<String, Object> match;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder providerId(String providerId) {
                this.providerId = providerId;
                return this;
            }

            public Builder match(Map<String, Object> match) {
                this.match = match;
                return this;
            }

            public RoutingRule build() {
                Objects.requireNonNull(providerId, "providerId is required");
                return new RoutingRule(this);
            }
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String defaultProviderId;
        private List<RoutingRule> rules;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder defaultProviderId(String defaultProviderId) {
            this.defaultProviderId = defaultProviderId;
            return this;
        }

        public Builder rules(List<RoutingRule> rules) {
            this.rules = rules;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ModelRoutingDefinition build() {
            Objects.requireNonNull(id, "routing id is required");
            return new ModelRoutingDefinition(this);
        }
    }
}
