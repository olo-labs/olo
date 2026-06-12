package org.olo.definition.node;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.error.OnFailureDefinition;
import org.olo.definition.execution.ExecutionKind;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.parallel.JoinDefinition;
import org.olo.definition.runtime.RuntimeBindingDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;

import java.util.List;
import java.util.Objects;

/**
 * How a {@link NodeDefinition} is scheduled and executed at runtime.
 * Grouped under {@code execution} so node artifacts read as: what it is ({@code capability}),
 * what it consumes ({@code inputs}), what it produces ({@code outputs}), how it runs ({@code execution}).
 */
@JsonDeserialize(builder = NodeExecutionDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NodeExecutionDefinition {

    private final ExecutionKind executionKind;
    private final ExecutionModel executionModel;
    private final String subtype;
    private final String version;
    private final WorkflowReferenceDefinition workflow;
    private final JoinDefinition join;
    private final RuntimeBindingDefinition runtimeBinding;
    private final List<NodeRouterDefinition> routers;
    private final OnFailureDefinition onFailure;
    private final HumanApprovalDefinition approval;

    private NodeExecutionDefinition(Builder builder) {
        this.executionKind = builder.executionKind;
        this.executionModel = builder.executionModel;
        this.subtype = builder.subtype;
        this.version = builder.version;
        this.workflow = builder.workflow;
        this.join = builder.join;
        this.runtimeBinding = builder.runtimeBinding;
        this.routers = builder.routers == null ? null : List.copyOf(builder.routers);
        this.onFailure = builder.onFailure;
        this.approval = builder.approval;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ExecutionKind getExecutionKind() {
        return executionKind;
    }

    public ExecutionModel getExecutionModel() {
        return executionModel;
    }

    public String getSubtype() {
        return subtype;
    }

    public String getVersion() {
        return version;
    }

    @JsonProperty("workflowRef")
    @JsonAlias("workflow")
    public WorkflowReferenceDefinition getWorkflow() {
        return workflow;
    }

    public JoinDefinition getJoin() {
        return join;
    }

    public RuntimeBindingDefinition getRuntimeBinding() {
        return runtimeBinding;
    }

    public List<NodeRouterDefinition> getRouters() {
        return routers == null ? List.of() : routers;
    }

    public OnFailureDefinition getOnFailure() {
        return onFailure;
    }

    public HumanApprovalDefinition getApproval() {
        return approval;
    }

    boolean isEffectivelyEmpty() {
        return executionKind == null
                && executionModel == null
                && subtype == null
                && version == null
                && workflow == null
                && join == null
                && runtimeBinding == null
                && (routers == null || routers.isEmpty())
                && onFailure == null
                && approval == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeExecutionDefinition that)) {
            return false;
        }
        return executionKind == that.executionKind
                && executionModel == that.executionModel
                && Objects.equals(subtype, that.subtype)
                && Objects.equals(version, that.version)
                && Objects.equals(workflow, that.workflow)
                && Objects.equals(join, that.join)
                && Objects.equals(runtimeBinding, that.runtimeBinding)
                && Objects.equals(getRouters(), that.getRouters())
                && Objects.equals(onFailure, that.onFailure)
                && Objects.equals(approval, that.approval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                executionKind,
                executionModel,
                subtype,
                version,
                workflow,
                join,
                runtimeBinding,
                getRouters(),
                onFailure,
                approval);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private ExecutionKind executionKind;
        private ExecutionModel executionModel;
        private String subtype;
        private String version;
        @JsonProperty("workflowRef")
        @JsonAlias("workflow")
        private WorkflowReferenceDefinition workflow;
        private JoinDefinition join;
        private RuntimeBindingDefinition runtimeBinding;
        private List<NodeRouterDefinition> routers;
        private OnFailureDefinition onFailure;
        private HumanApprovalDefinition approval;

        public Builder executionKind(ExecutionKind executionKind) {
            this.executionKind = executionKind;
            return this;
        }

        public Builder executionModel(ExecutionModel executionModel) {
            this.executionModel = executionModel;
            return this;
        }

        public Builder subtype(String subtype) {
            this.subtype = subtype;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder workflow(WorkflowReferenceDefinition workflow) {
            this.workflow = workflow;
            return this;
        }

        public Builder join(JoinDefinition join) {
            this.join = join;
            return this;
        }

        public Builder runtimeBinding(RuntimeBindingDefinition runtimeBinding) {
            this.runtimeBinding = runtimeBinding;
            return this;
        }

        public Builder routers(List<NodeRouterDefinition> routers) {
            this.routers = routers;
            return this;
        }

        public Builder addRouter(NodeRouterDefinition router) {
            if (this.routers == null) {
                this.routers = new java.util.ArrayList<>();
            }
            this.routers.add(router);
            return this;
        }

        public Builder onFailure(OnFailureDefinition onFailure) {
            this.onFailure = onFailure;
            return this;
        }

        public Builder approval(HumanApprovalDefinition approval) {
            this.approval = approval;
            return this;
        }

        public NodeExecutionDefinition build() {
            NodeExecutionDefinition built = new NodeExecutionDefinition(this);
            return built.isEffectivelyEmpty() ? null : built;
        }
    }
}
