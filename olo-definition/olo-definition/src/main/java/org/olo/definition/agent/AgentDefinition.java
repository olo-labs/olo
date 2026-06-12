package org.olo.definition.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.runtime.RuntimeBindingDefinition;
import org.olo.definition.runtime.WorkflowRuntimeDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative agent registry entry ({@code type} {@code AGENT}).
 * {@code capability} is what the planner sees; {@code workflow} / {@code workflowRef} links to the executable artifact;
 * {@code runtimeBinding} selects registry or custom executor.
 */
@JsonDeserialize(builder = AgentDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AgentDefinition {

    public static final String TYPE = "AGENT";

    private final String id;
    private final String type;
    private final CapabilityDefinition capability;
    private final WorkflowReferenceDefinition workflow;
    private final RuntimeBindingDefinition runtimeBinding;
    private final Map<String, Object> configuration;
    private final WorkflowRuntimeDefinition runtime;

    private AgentDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type == null ? TYPE : builder.type;
        this.capability = builder.capability;
        this.workflow = builder.workflow;
        this.runtimeBinding = builder.runtimeBinding;
        this.configuration = builder.configuration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.configuration));
        this.runtime = builder.runtime;
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

    @JsonProperty("workflowRef")
    @JsonAlias("workflow")
    public WorkflowReferenceDefinition getWorkflow() {
        return workflow;
    }

    public RuntimeBindingDefinition getRuntimeBinding() {
        return runtimeBinding;
    }

    /** Integration settings (e.g. {@code systemPrompt}). */
    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public WorkflowRuntimeDefinition getRuntime() {
        return runtime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(capability, that.capability)
                && Objects.equals(workflow, that.workflow)
                && Objects.equals(runtimeBinding, that.runtimeBinding)
                && Objects.equals(configuration, that.configuration)
                && Objects.equals(runtime, that.runtime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, capability, workflow, runtimeBinding, configuration, runtime);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String type;
        private CapabilityDefinition capability;
        @JsonProperty("workflowRef")
        @JsonAlias("workflow")
        private WorkflowReferenceDefinition workflow;
        private RuntimeBindingDefinition runtimeBinding;
        private Map<String, Object> configuration;
        private WorkflowRuntimeDefinition runtime;

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

        public Builder workflow(WorkflowReferenceDefinition workflow) {
            this.workflow = workflow;
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

        public Builder systemPrompt(String systemPrompt) {
            return putConfiguration("systemPrompt", systemPrompt);
        }

        public Builder runtime(WorkflowRuntimeDefinition runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder executionModel(ExecutionModel executionModel) {
            if (this.runtime == null) {
                this.runtime = WorkflowRuntimeDefinition.builder().executionModel(executionModel).build();
            } else if (executionModel != null) {
                this.runtime = WorkflowRuntimeDefinition.builder()
                        .contractVersion(runtime.getContractVersion())
                        .executionModel(executionModel)
                        .capabilities(runtime.getCapabilities())
                        .defaultTimeout(runtime.getDefaultTimeout())
                        .build();
            }
            return this;
        }

        public AgentDefinition build() {
            Objects.requireNonNull(id, "agent id is required");
            Objects.requireNonNull(capability, "agent capability is required");
            Objects.requireNonNull(workflow, "agent workflow reference is required");
            return new AgentDefinition(this);
        }
    }
}
