package org.olo.definition.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reference to a versioned workflow artifact (agent definition, reusable subgraph).
 * Required on {@code AGENT} and {@code WORKFLOW_REF} nodes.
 */
@JsonDeserialize(builder = WorkflowReferenceDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WorkflowReferenceDefinition {

    private final String workflowId;
    private final String version;
    private final Map<String, String> inputMapping;
    private final Map<String, String> outputMapping;

    private WorkflowReferenceDefinition(Builder builder) {
        this.workflowId = builder.workflowId;
        this.version = builder.version;
        this.inputMapping = builder.inputMapping == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.inputMapping));
        this.outputMapping = builder.outputMapping == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.outputMapping));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getInputMapping() {
        return inputMapping;
    }

    public Map<String, String> getOutputMapping() {
        return outputMapping;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowReferenceDefinition that)) {
            return false;
        }
        return Objects.equals(workflowId, that.workflowId)
                && Objects.equals(version, that.version)
                && Objects.equals(inputMapping, that.inputMapping)
                && Objects.equals(outputMapping, that.outputMapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflowId, version, inputMapping, outputMapping);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String workflowId;
        private String version;
        private Map<String, String> inputMapping;
        private Map<String, String> outputMapping;

        public Builder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder inputMapping(Map<String, String> inputMapping) {
            this.inputMapping = inputMapping;
            return this;
        }

        public Builder outputMapping(Map<String, String> outputMapping) {
            this.outputMapping = outputMapping;
            return this;
        }

        public WorkflowReferenceDefinition build() {
            Objects.requireNonNull(workflowId, "workflowId is required");
            return new WorkflowReferenceDefinition(this);
        }
    }
}
