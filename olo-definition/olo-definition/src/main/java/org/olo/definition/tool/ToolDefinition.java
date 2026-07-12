/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.runtime.RuntimeBindingDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative tool artifact ({@code type} {@code TOOL}) with a planner-readable {@code capability}
 * {@code runtimeBinding} for registry or custom execution, and optional {@code configuration} for integration knobs.
 */
@JsonDeserialize(builder = ToolDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ToolDefinition {

    public static final String TYPE = "TOOL";

    private final String id;
    private final String type;
    private final CapabilityDefinition capability;
    private final RuntimeBindingDefinition runtimeBinding;
    private final Map<String, Object> configuration;

    private ToolDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type == null ? TYPE : builder.type;
        this.capability = builder.capability;
        this.runtimeBinding = builder.runtimeBinding;
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

    public CapabilityDefinition getCapability() {
        return capability;
    }

    public RuntimeBindingDefinition getRuntimeBinding() {
        return runtimeBinding;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ToolDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(capability, that.capability)
                && Objects.equals(runtimeBinding, that.runtimeBinding)
                && Objects.equals(configuration, that.configuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, capability, runtimeBinding, configuration);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String type;
        private CapabilityDefinition capability;
        private RuntimeBindingDefinition runtimeBinding;
        private Map<String, Object> configuration;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder capability(CapabilityDefinition capability) {
            this.capability = capability;
            return this;
        }

        public Builder runtimeBinding(RuntimeBindingDefinition runtimeBinding) {
            this.runtimeBinding = runtimeBinding;
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

        public ToolDefinition build() {
            Objects.requireNonNull(id, "tool id is required");
            Objects.requireNonNull(capability, "tool capability is required");
            return new ToolDefinition(this);
        }
    }
}
