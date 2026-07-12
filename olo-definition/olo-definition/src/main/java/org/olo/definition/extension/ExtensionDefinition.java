/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.extension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pluggable extension reference (tools, vector stores, MCP servers, etc.).
 */
@JsonDeserialize(builder = ExtensionDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ExtensionDefinition {

    private final String id;
    private final String type;
    private final Map<String, Object> configuration;

    private ExtensionDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
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

    public String getType() {
        return type;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExtensionDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(configuration, that.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, configuration);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String type;
        private Map<String, Object> configuration;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
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

        public ExtensionDefinition build() {
            Objects.requireNonNull(id, "extension id is required");
            Objects.requireNonNull(type, "extension type is required");
            return new ExtensionDefinition(this);
        }
    }
}
