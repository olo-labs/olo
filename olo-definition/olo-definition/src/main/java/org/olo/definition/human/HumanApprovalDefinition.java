package org.olo.definition.human;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Declarative human-in-the-loop step (approval, review, or input). Serialized as {@code approval}
 * on {@link org.olo.definition.node.NodeDefinition} nodes of type {@code HUMAN}.
 */
@JsonDeserialize(builder = HumanApprovalDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class HumanApprovalDefinition {

    private final String title;
    private final String description;
    private final List<String> approvers;
    private final Long timeoutSeconds;
    private final boolean requireCommentOnReject;

    private HumanApprovalDefinition(Builder builder) {
        this.title = builder.title;
        this.description = builder.description;
        this.approvers = builder.approvers == null ? List.of() : List.copyOf(builder.approvers);
        this.timeoutSeconds = builder.timeoutSeconds;
        this.requireCommentOnReject = builder.requireCommentOnReject;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getApprovers() {
        return approvers;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public boolean isRequireCommentOnReject() {
        return requireCommentOnReject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HumanApprovalDefinition that)) {
            return false;
        }
        return requireCommentOnReject == that.requireCommentOnReject
                && Objects.equals(title, that.title)
                && Objects.equals(description, that.description)
                && Objects.equals(approvers, that.approvers)
                && Objects.equals(timeoutSeconds, that.timeoutSeconds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, description, approvers, timeoutSeconds, requireCommentOnReject);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String title;
        private String description;
        private List<String> approvers;
        private Long timeoutSeconds;
        private boolean requireCommentOnReject;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder approvers(List<String> approvers) {
            this.approvers = approvers;
            return this;
        }

        public Builder addApprover(String approver) {
            if (this.approvers == null) {
                this.approvers = new java.util.ArrayList<>();
            }
            this.approvers.add(approver);
            return this;
        }

        public Builder timeoutSeconds(Long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder requireCommentOnReject(boolean requireCommentOnReject) {
            this.requireCommentOnReject = requireCommentOnReject;
            return this;
        }

        public HumanApprovalDefinition build() {
            Objects.requireNonNull(title, "approval title is required");
            return new HumanApprovalDefinition(this);
        }
    }
}
