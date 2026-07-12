/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative routing rule attached to a {@link NodeDefinition} (ports, targets, match criteria).
 */
@JsonDeserialize(builder = NodeRouterDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NodeRouterDefinition {

    private final String id;
    private final String name;
    private final String targetPort;
    private final String targetNodeId;
    private final String providerId;
    private final Map<String, Object> match;
    private final Map<String, Object> configuration;

    private NodeRouterDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.targetPort = builder.targetPort;
        this.targetNodeId = builder.targetNodeId;
        this.providerId = builder.providerId;
        this.match = builder.match == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.match));
        this.configuration = builder.configuration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.configuration));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTargetPort() {
        return targetPort;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getProviderId() {
        return providerId;
    }

    public Map<String, Object> getMatch() {
        return match;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeRouterDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(targetPort, that.targetPort)
                && Objects.equals(targetNodeId, that.targetNodeId)
                && Objects.equals(providerId, that.providerId)
                && Objects.equals(match, that.match)
                && Objects.equals(configuration, that.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, targetPort, targetNodeId, providerId, match, configuration);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String targetPort;
        private String targetNodeId;
        private String providerId;
        private Map<String, Object> match;
        private Map<String, Object> configuration;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder targetPort(String targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
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

        public Builder configuration(Map<String, Object> configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder putConfiguration(String key, Object value) {
            if (this.configuration == null) {
                this.configuration = new LinkedHashMap<>();
            }
            this.configuration.put(key, value);
            return this;
        }

        public NodeRouterDefinition build() {
            return new NodeRouterDefinition(this);
        }
    }
}
