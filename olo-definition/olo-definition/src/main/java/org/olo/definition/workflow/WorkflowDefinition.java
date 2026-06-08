package org.olo.definition.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.runtime.RuntimeBindingDefinition;
import org.olo.definition.edge.EdgeDefinition;
import org.olo.definition.extension.ExtensionDefinition;
import org.olo.definition.hook.HookDefinition;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.model.ModelProviderDefinition;
import org.olo.definition.model.ModelRoutingDefinition;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.parameter.WorkflowParameterDefinition;
import org.olo.definition.state.StateFieldDefinition;
import org.olo.definition.variable.VariableDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Root declarative artifact for an AI orchestration workflow graph.
 * Serializable to JSON/YAML; contains no execution or runtime state.
 */
@JsonDeserialize(builder = WorkflowDefinition.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({
    "id",
    "name",
    "role",
    "shortDescription",
    "emoji",
    "queue",
    "workflowType",
    "runAgain",
    "longDescription",
    "isExternalWorkflow",
    "isChildWorkflow",
    "childWorkflows",
    "version",
    "capability",
    "runtimeBinding",
    "inputs",
    "state",
    "parameters",
    "nodes",
    "edges",
    "variables",
    "tools",
    "agents",
    "hooks",
    "modelProviders",
    "modelRouting",
    "extensions",
    "metadata"
})
public final class WorkflowDefinition {

    private final String id;
    private final String name;
    private final String role;
    private final String shortDescription;
    private final String emoji;
    private final String queue;
    private final String workflowType;
    private final Boolean runAgain;
    private final String longDescription;
    private final Boolean isExternalWorkflow;
    private final Boolean isChildWorkflow;
    private final List<ChildWorkflowDefinition> childWorkflows;
    private final String version;
    private final List<NodeDefinition> nodes;
    private final List<EdgeDefinition> edges;
    private final Map<String, WorkflowInputDefinition> inputs;
    private final Map<String, StateFieldDefinition> state;
    private final Map<String, WorkflowParameterDefinition> parameters;
    private final List<VariableDefinition> variables;
    private final List<ModelProviderDefinition> modelProviders;
    private final List<ModelRoutingDefinition> modelRouting;
    private final List<ExtensionDefinition> extensions;
    private final Map<String, Object> metadata;
    private final CapabilityDefinition capability;
    private final RuntimeBindingDefinition runtimeBinding;
    private final List<ToolDefinition> tools;
    private final List<AgentDefinition> agents;
    private final List<HookDefinition> hooks;

    private WorkflowDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.role = builder.role;
        this.shortDescription = builder.shortDescription;
        this.emoji = builder.emoji;
        this.queue = builder.queue;
        this.workflowType = builder.workflowType;
        this.runAgain = builder.runAgain;
        this.longDescription = builder.longDescription;
        this.isExternalWorkflow = builder.isExternalWorkflow;
        this.isChildWorkflow = builder.isChildWorkflow;
        this.childWorkflows = builder.childWorkflows == null ? List.of() : List.copyOf(builder.childWorkflows);
        this.version = builder.version;
        this.capability = builder.capability;
        this.runtimeBinding = builder.runtimeBinding;
        this.tools = builder.tools == null ? List.of() : List.copyOf(builder.tools);
        this.agents = builder.agents == null ? List.of() : List.copyOf(builder.agents);
        this.hooks = builder.hooks == null ? List.of() : List.copyOf(builder.hooks);
        this.nodes = builder.nodes == null ? List.of() : List.copyOf(builder.nodes);
        this.edges = builder.edges == null ? List.of() : List.copyOf(builder.edges);
        this.inputs = copyMap(builder.inputs);
        this.state = copyMap(builder.state);
        this.parameters = copyMap(builder.parameters);
        this.variables = builder.variables == null ? List.of() : List.copyOf(builder.variables);
        this.modelProviders = builder.modelProviders == null ? List.of() : List.copyOf(builder.modelProviders);
        this.modelRouting = builder.modelRouting == null ? List.of() : List.copyOf(builder.modelRouting);
        this.extensions = builder.extensions == null ? List.of() : List.copyOf(builder.extensions);
        this.metadata = builder.metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Chat UI display name for this workflow preset ({@code display_name} in legacy profiles). */
    public String getRole() {
        return role;
    }

    /** Chat UI short summary / tooltip ({@code display_summary} in legacy profiles). */
    public String getShortDescription() {
        return shortDescription;
    }

    /** Optional emoji shown beside the preset in the chat UI. */
    public String getEmoji() {
        return emoji;
    }

    /** Temporal task queue name for routing runs of this workflow. */
    public String getQueue() {
        return queue;
    }

    /** Temporal workflow type name (e.g. {@code olo}). */
    public String getWorkflowType() {
        return workflowType;
    }

    /** When true, the chat UI offers this preset in per-message run-again menus. */
    @JsonProperty("runAgain")
    public Boolean isRunAgain() {
        return runAgain;
    }

    /** Extended human-readable description with full context. */
    public String getLongDescription() {
        return longDescription;
    }

    /** When true, the workflow artifact is owned or resolved outside the local registry. */
    @JsonProperty("isExternalWorkflow")
    public Boolean isExternalWorkflow() {
        return isExternalWorkflow;
    }

    /** When true, the workflow is invoked as a child/sub-workflow of a parent orchestration. */
    @JsonProperty("isChildWorkflow")
    public Boolean isChildWorkflow() {
        return isChildWorkflow;
    }

    /** Child workflow artifacts composed or invoked by this workflow. */
    public List<ChildWorkflowDefinition> getChildWorkflows() {
        return childWorkflows;
    }

    public String getVersion() {
        return version;
    }

    public List<NodeDefinition> getNodes() {
        return nodes;
    }

    public List<EdgeDefinition> getEdges() {
        return edges;
    }

    /** Invocation inputs supplied when the workflow starts (map key = input name). */
    public Map<String, WorkflowInputDefinition> getInputs() {
        return inputs;
    }

    /** Shared mutable workflow state schema (map key = state field name). */
    public Map<String, StateFieldDefinition> getState() {
        return state;
    }

    /** Runtime tuning parameters (e.g. temperature), not state or invocation inputs. */
    public Map<String, WorkflowParameterDefinition> getParameters() {
        return parameters;
    }

    /** @deprecated use {@link #getInputs()} */
    @Deprecated
    public List<VariableDefinition> getVariables() {
        return variables;
    }

    public List<ModelProviderDefinition> getModelProviders() {
        return modelProviders;
    }

    public List<ModelRoutingDefinition> getModelRouting() {
        return modelRouting;
    }

    public List<ExtensionDefinition> getExtensions() {
        return extensions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public CapabilityDefinition getCapability() {
        return capability;
    }

    public RuntimeBindingDefinition getRuntimeBinding() {
        return runtimeBinding;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public List<AgentDefinition> getAgents() {
        return agents;
    }

    /** Cross-cutting lifecycle hooks (PRE, ON_ERROR, FINALLY) matched by node id pattern. */
    public List<HookDefinition> getHooks() {
        return hooks;
    }

    /**
     * Returns an independent immutable copy of this workflow definition.
     * <p>
     * The result is equal to this instance ({@link #equals(Object)}) but is a separate object with its own
     * unmodifiable collections. Use {@link #toBuilder()} when you intend to modify the copy before building.
     */
    public WorkflowDefinition copy() {
        return WorkflowBuilder.from(this).build();
    }

    /**
     * Returns a mutable builder seeded from this workflow (copy-on-write). Call {@link WorkflowBuilder#build()}
     * without changes to obtain an immutable copy equivalent to {@link #copy()}.
     */
    public WorkflowBuilder toBuilder() {
        return WorkflowBuilder.from(this);
    }

    /**
     * Returns an independent immutable copy of {@code source}.
     */
    public static WorkflowDefinition copyOf(WorkflowDefinition source) {
        Objects.requireNonNull(source, "source workflow is required");
        return source.copy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkflowDefinition that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(role, that.role)
                && Objects.equals(shortDescription, that.shortDescription)
                && Objects.equals(emoji, that.emoji)
                && Objects.equals(queue, that.queue)
                && Objects.equals(workflowType, that.workflowType)
                && Objects.equals(runAgain, that.runAgain)
                && Objects.equals(longDescription, that.longDescription)
                && Objects.equals(isExternalWorkflow, that.isExternalWorkflow)
                && Objects.equals(isChildWorkflow, that.isChildWorkflow)
                && Objects.equals(childWorkflows, that.childWorkflows)
                && Objects.equals(version, that.version)
                && Objects.equals(nodes, that.nodes)
                && Objects.equals(edges, that.edges)
                && Objects.equals(inputs, that.inputs)
                && Objects.equals(state, that.state)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(variables, that.variables)
                && Objects.equals(modelProviders, that.modelProviders)
                && Objects.equals(modelRouting, that.modelRouting)
                && Objects.equals(extensions, that.extensions)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(capability, that.capability)
                && Objects.equals(runtimeBinding, that.runtimeBinding)
                && Objects.equals(tools, that.tools)
                && Objects.equals(agents, that.agents)
                && Objects.equals(hooks, that.hooks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                name,
                role,
                shortDescription,
                emoji,
                queue,
                workflowType,
                runAgain,
                longDescription,
                isExternalWorkflow,
                isChildWorkflow,
                childWorkflows,
                version,
                nodes,
                edges,
                inputs,
                state,
                parameters,
                variables,
                modelProviders,
                modelRouting,
                extensions,
                metadata,
                capability,
                runtimeBinding,
                tools,
                agents,
                hooks);
    }

    @Override
    public String toString() {
        return "WorkflowDefinition{id='" + id + "', name='" + name + "', role='" + role + "', nodes=" + nodes.size() + "}";
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String role;
        private String shortDescription;
        private String emoji;
        private String queue;
        private String workflowType;
        private Boolean runAgain;
        private String longDescription;
        @JsonProperty("isExternalWorkflow")
        private Boolean isExternalWorkflow;
        @JsonProperty("isChildWorkflow")
        private Boolean isChildWorkflow;
        private List<ChildWorkflowDefinition> childWorkflows;
        private String version;
        private List<NodeDefinition> nodes;
        private List<EdgeDefinition> edges;
        private Map<String, WorkflowInputDefinition> inputs;
        private Map<String, StateFieldDefinition> state;
        private Map<String, WorkflowParameterDefinition> parameters;
        private List<VariableDefinition> variables;
        private List<ModelProviderDefinition> modelProviders;
        private List<ModelRoutingDefinition> modelRouting;
        private List<ExtensionDefinition> extensions;
        private Map<String, Object> metadata;
        private CapabilityDefinition capability;
        private RuntimeBindingDefinition runtimeBinding;
        private List<ToolDefinition> tools;
        private List<AgentDefinition> agents;
        private List<HookDefinition> hooks;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder shortDescription(String shortDescription) {
            this.shortDescription = shortDescription;
            return this;
        }

        public Builder emoji(String emoji) {
            this.emoji = emoji;
            return this;
        }

        public Builder queue(String queue) {
            this.queue = queue;
            return this;
        }

        public Builder workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }

        public Builder runAgain(Boolean runAgain) {
            this.runAgain = runAgain;
            return this;
        }

        public Builder longDescription(String longDescription) {
            this.longDescription = longDescription;
            return this;
        }

        public Builder isExternalWorkflow(Boolean isExternalWorkflow) {
            this.isExternalWorkflow = isExternalWorkflow;
            return this;
        }

        public Builder isChildWorkflow(Boolean isChildWorkflow) {
            this.isChildWorkflow = isChildWorkflow;
            return this;
        }

        public Builder childWorkflows(List<ChildWorkflowDefinition> childWorkflows) {
            this.childWorkflows = childWorkflows;
            return this;
        }

        public Builder addChildWorkflow(ChildWorkflowDefinition childWorkflow) {
            if (this.childWorkflows == null) {
                this.childWorkflows = new java.util.ArrayList<>();
            }
            this.childWorkflows.add(childWorkflow);
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder nodes(List<NodeDefinition> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder addNode(NodeDefinition node) {
            if (this.nodes == null) {
                this.nodes = new java.util.ArrayList<>();
            }
            this.nodes.add(node);
            return this;
        }

        public Builder edges(List<EdgeDefinition> edges) {
            this.edges = edges;
            return this;
        }

        public Builder addEdge(EdgeDefinition edge) {
            if (this.edges == null) {
                this.edges = new java.util.ArrayList<>();
            }
            this.edges.add(edge);
            return this;
        }

        public Builder inputs(Map<String, WorkflowInputDefinition> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder putInput(String name, WorkflowInputDefinition input) {
            if (this.inputs == null) {
                this.inputs = new LinkedHashMap<>();
            }
            this.inputs.put(name, input);
            return this;
        }

        public Builder state(Map<String, StateFieldDefinition> state) {
            this.state = state;
            return this;
        }

        public Builder putState(String name, StateFieldDefinition field) {
            if (this.state == null) {
                this.state = new LinkedHashMap<>();
            }
            this.state.put(name, field);
            return this;
        }

        public Builder parameters(Map<String, WorkflowParameterDefinition> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder putParameter(String name, WorkflowParameterDefinition parameter) {
            if (this.parameters == null) {
                this.parameters = new LinkedHashMap<>();
            }
            this.parameters.put(name, parameter);
            return this;
        }

        /** @deprecated use {@link #putInput(String, WorkflowInputDefinition)} */
        @Deprecated
        public Builder variables(List<VariableDefinition> variables) {
            this.variables = variables;
            return this;
        }

        public Builder modelProviders(List<ModelProviderDefinition> modelProviders) {
            this.modelProviders = modelProviders;
            return this;
        }

        public Builder modelRouting(List<ModelRoutingDefinition> modelRouting) {
            this.modelRouting = modelRouting;
            return this;
        }

        public Builder extensions(List<ExtensionDefinition> extensions) {
            this.extensions = extensions;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
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

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder addTool(ToolDefinition tool) {
            if (this.tools == null) {
                this.tools = new java.util.ArrayList<>();
            }
            this.tools.add(tool);
            return this;
        }

        public Builder agents(List<AgentDefinition> agents) {
            this.agents = agents;
            return this;
        }

        public Builder addAgent(AgentDefinition agent) {
            if (this.agents == null) {
                this.agents = new java.util.ArrayList<>();
            }
            this.agents.add(agent);
            return this;
        }

        public Builder hooks(List<HookDefinition> hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder addHook(HookDefinition hook) {
            if (this.hooks == null) {
                this.hooks = new java.util.ArrayList<>();
            }
            this.hooks.add(hook);
            return this;
        }

        public WorkflowDefinition build() {
            Objects.requireNonNull(id, "workflow id is required");
            return new WorkflowDefinition(this);
        }
    }

    private static <V> Map<String, V> copyMap(Map<String, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
