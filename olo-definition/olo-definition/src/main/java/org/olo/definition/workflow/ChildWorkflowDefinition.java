package org.olo.definition.workflow;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

/**
 * Declares a child workflow composed or invoked by a parent workflow.
 * {@link #getQueue()} is the Temporal task queue name for dispatching the child.
 */
@JsonDeserialize(builder = ChildWorkflowDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ChildWorkflowDefinition {

    private final String queue;
    private final String workflowVersion;

    private ChildWorkflowDefinition(Builder builder) {
        this.queue = builder.queue;
        this.workflowVersion = builder.workflowVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Temporal task queue name for this child workflow. */
    @JsonProperty("queue")
    public String getQueue() {
        return queue;
    }

    @JsonProperty("workflowVersion")
    public String getWorkflowVersion() {
        return workflowVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChildWorkflowDefinition that)) {
            return false;
        }
        return Objects.equals(queue, that.queue)
                && Objects.equals(workflowVersion, that.workflowVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queue, workflowVersion);
    }

    @Override
    public String toString() {
        return "ChildWorkflowDefinition{queue='"
                + queue
                + "', workflowVersion='"
                + workflowVersion
                + "'}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        @JsonAlias("workflowId")
        private String queue;
        @JsonProperty("workflowVersion")
        private String workflowVersion;

        public Builder queue(String queue) {
            this.queue = queue;
            return this;
        }

        /** @deprecated use {@link #queue(String)} — legacy JSON used {@code workflowId}. */
        @Deprecated
        public Builder workflowId(String workflowId) {
            return queue(workflowId);
        }

        public Builder workflowVersion(String workflowVersion) {
            this.workflowVersion = workflowVersion;
            return this;
        }

        public ChildWorkflowDefinition build() {
            Objects.requireNonNull(queue, "queue is required");
            return new ChildWorkflowDefinition(this);
        }
    }
}
