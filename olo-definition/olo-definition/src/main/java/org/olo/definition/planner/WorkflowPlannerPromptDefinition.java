package org.olo.definition.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Workflow-level planner prompt template referenced by agent nodes via {@code promptRef}.
 * Placeholders resolve against workflow {@code variables[]}, not embedded parameters.
 */
@JsonDeserialize(builder = WorkflowPlannerPromptDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WorkflowPlannerPromptDefinition {

    public static final String DEFAULT_PROMPT_ID = "default-prompt";

    public static final String DEFAULT_PROMPT_TEMPLATE =
            """
            You are a research planner.

            Investigate {message}.

            Use available capabilities when needed.
            Delegate work when another agent is more suitable.""";

    private final String id;
    private final String name;
    private final String promptTemplate;

    private WorkflowPlannerPromptDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.promptTemplate = builder.promptTemplate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WorkflowPlannerPromptDefinition agentDefault() {
        return forPreset("agent");
    }

    /** Role-specific default prompt for a workflow preset id. */
    public static WorkflowPlannerPromptDefinition forPreset(String presetId) {
        return builder()
                .id(DEFAULT_PROMPT_ID)
                .name(promptName(presetId))
                .promptTemplate(promptTemplate(presetId))
                .build();
    }

    private static String promptName(String presetId) {
        return switch (presetId) {
            case "agent" -> "Default planner prompt";
            case "planner" -> "Default planner prompt";
            case "architect" -> "Default architect prompt";
            case "reviewer" -> "Default reviewer prompt";
            case "ask" -> "Default ask prompt";
            case "fast" -> "Default fast prompt";
            case "detailed" -> "Default detailed prompt";
            case "strict" -> "Default strict prompt";
            case "summary" -> "Default summary prompt";
            case "teacher" -> "Default teacher prompt";
            case "debug" -> "Default debug prompt";
            case "minimal-echo" -> "Default echo prompt";
            default -> "Default prompt";
        };
    }

    private static String promptTemplate(String presetId) {
        return switch (presetId) {
            case "agent" -> DEFAULT_PROMPT_TEMPLATE;
            case "planner" ->
                    """
                    You are a planning specialist.

                    Create a structured plan for {message}.

                    Break work into clear, actionable steps.""";
            case "architect" ->
                    """
                    You are a system architect.

                    Design architecture guidance for {message}.

                    Cover components, boundaries, and trade-offs.""";
            case "reviewer" ->
                    """
                    You are a critical reviewer.

                    Review {message} thoroughly.

                    Highlight risks, gaps, and concrete improvements.""";
            case "ask" ->
                    """
                    You answer questions directly and clearly.

                    Question: {message}""";
            case "fast" ->
                    """
                    You provide quick, concise responses.

                    Address {message} in as few words as practical.""";
            case "detailed" ->
                    """
                    You provide thorough, in-depth explanations.

                    Explain {message} with depth, context, and examples.""";
            case "strict" ->
                    """
                    You follow rules precisely and avoid speculation.

                    Address {message} with exact, constraint-aware reasoning.""";
            case "summary" ->
                    """
                    You summarize content into key points.

                    Summarize {message} briefly.""";
            case "teacher" ->
                    """
                    You teach concepts step by step.

                    Teach {message} to a learner with progressive explanations.""";
            case "debug" ->
                    """
                    You provide verbose diagnostic output for troubleshooting.

                    Investigate {message} and explain reasoning in detail.""";
            case "minimal-echo" ->
                    """
                    You echo the caller input.

                    Return {message}.""";
            default ->
                    """
                    Address the request.

                    {message}""";
        };
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowPlannerPromptDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(promptTemplate, that.promptTemplate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, promptTemplate);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String promptTemplate;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder promptTemplate(String promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public WorkflowPlannerPromptDefinition build() {
            Objects.requireNonNull(id, "planner prompt id is required");
            Objects.requireNonNull(name, "planner prompt name is required");
            Objects.requireNonNull(promptTemplate, "planner prompt template is required");
            return new WorkflowPlannerPromptDefinition(this);
        }
    }
}
