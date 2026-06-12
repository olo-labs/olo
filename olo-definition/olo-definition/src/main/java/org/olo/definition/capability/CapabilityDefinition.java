package org.olo.definition.capability;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Planner-readable contract shared by workflows, tools, agents, and nodes.
 * Runtime graphs and {@code configuration} stay separate; planners consume only capability metadata.
 * <p>
 * {@link #getName()} is the capability descriptor title for planners and orchestrators — not the
 * workflow Studio label ({@link org.olo.definition.workflow.WorkflowDefinition#getLabel()}). The two
 * often match on presets (e.g. both {@code "Planner"}) but serve different layers: UI display vs
 * capability contract.
 * <p>
 * {@link #getRequiredInputs()} / {@link #getRequiredOutputs()} are semantic planner contracts — not the
 * same as workflow invocation {@code inputs} on {@link org.olo.definition.workflow.WorkflowDefinition}.
 */
@JsonDeserialize(builder = CapabilityDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class CapabilityDefinition {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> requiredInputs;
    private final List<String> requiredOutputs;
    private final List<String> tags;
    private final List<String> examples;
    private final Double cost;
    private final Double latency;
    private final Double confidence;
    private final List<String> toolRequirements;
    private final List<String> requiredContext;

    private CapabilityDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.requiredInputs = builder.requiredInputs == null ? List.of() : List.copyOf(builder.requiredInputs);
        this.requiredOutputs = builder.requiredOutputs == null ? List.of() : List.copyOf(builder.requiredOutputs);
        this.tags = builder.tags == null ? List.of() : List.copyOf(builder.tags);
        this.examples = builder.examples == null ? List.of() : List.copyOf(builder.examples);
        this.cost = builder.cost;
        this.latency = builder.latency;
        this.confidence = builder.confidence;
        this.toolRequirements =
                builder.toolRequirements == null ? List.of() : List.copyOf(builder.toolRequirements);
        this.requiredContext =
                builder.requiredContext == null ? List.of() : List.copyOf(builder.requiredContext);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    /** Capability descriptor title (planner contract) — not {@link org.olo.definition.workflow.WorkflowDefinition#getLabel()}. */
    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @JsonProperty("required_inputs")
    public List<String> getRequiredInputs() {
        return requiredInputs;
    }

    @JsonProperty("required_outputs")
    public List<String> getRequiredOutputs() {
        return requiredOutputs;
    }

    /** @deprecated use {@link #getRequiredInputs()} */
    @Deprecated
    @JsonIgnore
    public List<String> getInputs() {
        return requiredInputs;
    }

    /** @deprecated use {@link #getRequiredOutputs()} */
    @Deprecated
    @JsonIgnore
    public List<String> getOutputs() {
        return requiredOutputs;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getExamples() {
        return examples;
    }

    public Double getCost() {
        return cost;
    }

    @JsonProperty("latency")
    public Double getLatency() {
        return latency;
    }

    public Double getConfidence() {
        return confidence;
    }

    @JsonProperty("tool_requirements")
    public List<String> getToolRequirements() {
        return toolRequirements;
    }

    @JsonProperty("required_context")
    public List<String> getRequiredContext() {
        return requiredContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CapabilityDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(requiredInputs, that.requiredInputs)
                && Objects.equals(requiredOutputs, that.requiredOutputs)
                && Objects.equals(tags, that.tags)
                && Objects.equals(examples, that.examples)
                && Objects.equals(cost, that.cost)
                && Objects.equals(latency, that.latency)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(toolRequirements, that.toolRequirements)
                && Objects.equals(requiredContext, that.requiredContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                description,
                requiredInputs,
                requiredOutputs,
                tags,
                examples,
                cost,
                latency,
                confidence,
                toolRequirements,
                requiredContext);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String description;
        @JsonProperty("required_inputs")
        @JsonAlias("inputs")
        private List<String> requiredInputs;
        @JsonProperty("required_outputs")
        @JsonAlias("outputs")
        private List<String> requiredOutputs;
        private List<String> tags;
        private List<String> examples;
        private Double cost;
        private Double latency;
        private Double confidence;
        private List<String> toolRequirements;
        private List<String> requiredContext;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requiredInputs(List<String> requiredInputs) {
            this.requiredInputs = requiredInputs;
            return this;
        }

        public Builder addRequiredInput(String input) {
            if (this.requiredInputs == null) {
                this.requiredInputs = new java.util.ArrayList<>();
            }
            this.requiredInputs.add(input);
            return this;
        }

        /** @deprecated use {@link #addRequiredInput(String)} */
        @Deprecated
        public Builder addInput(String input) {
            return addRequiredInput(input);
        }

        /** @deprecated use {@link #requiredInputs(List)} */
        @Deprecated
        public Builder inputs(List<String> inputs) {
            this.requiredInputs = inputs;
            return this;
        }

        public Builder requiredOutputs(List<String> requiredOutputs) {
            this.requiredOutputs = requiredOutputs;
            return this;
        }

        public Builder addRequiredOutput(String output) {
            if (this.requiredOutputs == null) {
                this.requiredOutputs = new java.util.ArrayList<>();
            }
            this.requiredOutputs.add(output);
            return this;
        }

        /** @deprecated use {@link #addRequiredOutput(String)} */
        @Deprecated
        public Builder addOutput(String output) {
            return addRequiredOutput(output);
        }

        /** @deprecated use {@link #requiredOutputs(List)} */
        @Deprecated
        public Builder outputs(List<String> outputs) {
            this.requiredOutputs = outputs;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder addTag(String tag) {
            if (this.tags == null) {
                this.tags = new java.util.ArrayList<>();
            }
            this.tags.add(tag);
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples;
            return this;
        }

        public Builder addExample(String example) {
            if (this.examples == null) {
                this.examples = new java.util.ArrayList<>();
            }
            this.examples.add(example);
            return this;
        }

        public Builder cost(Double cost) {
            this.cost = cost;
            return this;
        }

        public Builder latency(Double latency) {
            this.latency = latency;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder toolRequirements(List<String> toolRequirements) {
            this.toolRequirements = toolRequirements;
            return this;
        }

        public Builder addToolRequirement(String toolRequirement) {
            if (this.toolRequirements == null) {
                this.toolRequirements = new java.util.ArrayList<>();
            }
            this.toolRequirements.add(toolRequirement);
            return this;
        }

        public Builder requiredContext(List<String> requiredContext) {
            this.requiredContext = requiredContext;
            return this;
        }

        public Builder addRequiredContext(String contextKey) {
            if (this.requiredContext == null) {
                this.requiredContext = new java.util.ArrayList<>();
            }
            this.requiredContext.add(contextKey);
            return this;
        }

        public CapabilityDefinition build() {
            Objects.requireNonNull(name, "capability name is required");
            Objects.requireNonNull(description, "capability description is required");
            return new CapabilityDefinition(this);
        }
    }
}
