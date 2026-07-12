/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative model provider configuration (OpenAI, Ollama, etc.) referenced by nodes at runtime.
 */
@JsonDeserialize(builder = ModelProviderDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ModelProviderDefinition {

    private final String id;
    private final String provider;
    private final String model;
    private final Map<String, Object> configuration;

    private ModelProviderDefinition(Builder builder) {
        this.id = builder.id;
        this.provider = builder.provider;
        this.model = builder.model;
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

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModelProviderDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(provider, that.provider)
                && Objects.equals(model, that.model)
                && Objects.equals(configuration, that.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider, model, configuration);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String provider;
        private String model;
        private Map<String, Object> configuration;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
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

        public ModelProviderDefinition build() {
            Objects.requireNonNull(id, "model provider id is required");
            Objects.requireNonNull(provider, "model provider name is required");
            return new ModelProviderDefinition(this);
        }
    }
}
