package org.olo.definition.node;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.error.OnFailureDefinition;
import org.olo.definition.execution.ExecutionKind;
import org.olo.definition.hook.NodeHooksDefinition;
import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.parallel.JoinDefinition;
import org.olo.definition.port.PortDefinition;
import org.olo.definition.runtime.RuntimeBindingDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single node in a workflow graph.
 * <p>
 * Structured as: {@code id}/{@code type} (identity), {@code capability} (what it is),
 * {@code ports} (typed connection contracts referenced by edges), {@code reads}/{@code writes}
 * (workflow state access), {@code execution} (how it runs), {@code configuration} (integration settings).
 */
@JsonDeserialize(builder = NodeDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NodeDefinition {

    private final String id;
    private final String type;
    private final CapabilityDefinition capability;
    private final List<PortDefinition> ports;
    private final NodeExecutionDefinition execution;
    private final List<String> reads;
    private final List<String> writes;
    private final Map<String, Object> configuration;
    private final NodeHooksDefinition hooks;

    private NodeDefinition(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.capability = builder.capability;
        this.ports = builder.ports == null ? List.of() : List.copyOf(builder.ports);
        this.execution = builder.execution;
        this.reads = builder.reads == null ? List.of() : List.copyOf(builder.reads);
        this.writes = builder.writes == null ? List.of() : List.copyOf(builder.writes);
        this.configuration = builder.configuration == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.configuration));
        this.hooks = builder.hooks;
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

    public List<PortDefinition> getPorts() {
        return ports;
    }

    public NodeExecutionDefinition getExecution() {
        return execution;
    }

    /** Declarative workflow state reads (e.g. {@code state.symbol}). */
    public List<String> getReads() {
        return reads;
    }

    /** Declarative workflow state writes (e.g. {@code state.analysis}). */
    public List<String> getWrites() {
        return writes;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    /**
     * Optional per-node hook bindings (implementation ids must be registered on workflow-level hooks).
     */
    public NodeHooksDefinition getHooks() {
        return hooks;
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public ExecutionKind getExecutionKind() {
        return execution == null ? null : execution.getExecutionKind();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public WorkflowReferenceDefinition getWorkflow() {
        return execution == null ? null : execution.getWorkflow();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public JoinDefinition getJoin() {
        return execution == null ? null : execution.getJoin();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public String getSubtype() {
        return execution == null ? null : execution.getSubtype();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public String getVersion() {
        return execution == null ? null : execution.getVersion();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public List<NodeRouterDefinition> getRouters() {
        return execution == null ? List.of() : execution.getRouters();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public OnFailureDefinition getOnFailure() {
        return execution == null ? null : execution.getOnFailure();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public HumanApprovalDefinition getApproval() {
        return execution == null ? null : execution.getApproval();
    }

    /** Convenience for validators and builders; serialized under {@code execution}. */
    @JsonIgnore
    public RuntimeBindingDefinition getRuntimeBinding() {
        return execution == null ? null : execution.getRuntimeBinding();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(capability, that.capability)
                && Objects.equals(ports, that.ports)
                && Objects.equals(execution, that.execution)
                && Objects.equals(reads, that.reads)
                && Objects.equals(writes, that.writes)
                && Objects.equals(configuration, that.configuration)
                && Objects.equals(hooks, that.hooks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, capability, ports, execution, reads, writes, configuration, hooks);
    }

    @Override
    public String toString() {
        return "NodeDefinition{id='" + id + "', type='" + type + "'}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String type;
        private CapabilityDefinition capability;
        private List<PortDefinition> ports;
        private NodeExecutionDefinition execution;
        private List<String> reads;
        private List<String> writes;
        private Map<String, Object> configuration;
        private NodeHooksDefinition hooks;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type == null ? null : type.value();
            return this;
        }

        public Builder capability(CapabilityDefinition capability) {
            this.capability = capability;
            return this;
        }

        public Builder execution(NodeExecutionDefinition execution) {
            this.execution = execution;
            return this;
        }

        public Builder reads(List<String> reads) {
            this.reads = reads;
            return this;
        }

        public Builder addRead(String read) {
            if (this.reads == null) {
                this.reads = new java.util.ArrayList<>();
            }
            this.reads.add(read);
            return this;
        }

        public Builder writes(List<String> writes) {
            this.writes = writes;
            return this;
        }

        public Builder addWrite(String write) {
            if (this.writes == null) {
                this.writes = new java.util.ArrayList<>();
            }
            this.writes.add(write);
            return this;
        }

        public Builder ports(List<PortDefinition> ports) {
            this.ports = ports;
            return this;
        }

        public Builder addPort(PortDefinition port) {
            if (this.ports == null) {
                this.ports = new java.util.ArrayList<>();
            }
            this.ports.add(port);
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

        public Builder executionKind(ExecutionKind executionKind) {
            ensureExecutionBuilder().executionKind(executionKind);
            return this;
        }

        public Builder workflow(WorkflowReferenceDefinition workflow) {
            ensureExecutionBuilder().workflow(workflow);
            return this;
        }

        public Builder runtimeBinding(RuntimeBindingDefinition runtimeBinding) {
            ensureExecutionBuilder().runtimeBinding(runtimeBinding);
            return this;
        }

        public Builder join(JoinDefinition join) {
            ensureExecutionBuilder().join(join);
            return this;
        }

        public Builder subtype(String subtype) {
            ensureExecutionBuilder().subtype(subtype);
            return this;
        }

        public Builder version(String version) {
            ensureExecutionBuilder().version(version);
            return this;
        }

        public Builder routers(List<NodeRouterDefinition> routers) {
            ensureExecutionBuilder().routers(routers);
            return this;
        }

        public Builder addRouter(NodeRouterDefinition router) {
            ensureExecutionBuilder().addRouter(router);
            return this;
        }

        public Builder onFailure(OnFailureDefinition onFailure) {
            ensureExecutionBuilder().onFailure(onFailure);
            return this;
        }

        public Builder approval(HumanApprovalDefinition approval) {
            ensureExecutionBuilder().approval(approval);
            return this;
        }

        public Builder hooks(NodeHooksDefinition hooks) {
            this.hooks = hooks;
            return this;
        }

        public NodeDefinition build() {
            Objects.requireNonNull(id, "node id is required");
            Objects.requireNonNull(type, "node type is required");
            if (execution == null && executionBuilder != null) {
                execution = executionBuilder.build();
            }
            return new NodeDefinition(this);
        }

        private NodeExecutionDefinition.Builder executionBuilder;

        private NodeExecutionDefinition.Builder ensureExecutionBuilder() {
            if (executionBuilder == null) {
                executionBuilder = NodeExecutionDefinition.builder();
            }
            return executionBuilder;
        }
    }
}
