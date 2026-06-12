package org.olo.definition.workflow;

import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.designer.DesignerDefinition;
import org.olo.definition.edge.EdgeDefinition;
import org.olo.definition.execution.ExecutionKind;
import org.olo.definition.execution.ExecutionModel;
import org.olo.definition.extension.ExtensionDefinition;
import org.olo.definition.hook.HookDefinition;
import org.olo.definition.runtime.RuntimeBindingDefinition;
import org.olo.spi.runtime.RuntimeCapability;
import org.olo.definition.runtime.WorkflowRuntimeDefinition;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.model.ModelProviderDefinition;
import org.olo.definition.model.ModelRoutingDefinition;
import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeType;
import org.olo.definition.port.PortDefinition;
import org.olo.definition.port.PortDirection;
import org.olo.definition.port.PortUiDefinition;
import org.olo.definition.parameter.AgentWorkflowParameters;
import org.olo.definition.parameter.WorkflowParameterDefinition;
import org.olo.definition.runtime.AgentDelegationPolicy;
import org.olo.definition.runtime.RuntimeDelegationDefinition;
import org.olo.definition.planner.AgentAvailableAgents;
import org.olo.definition.planner.AgentReferenceDefinition;
import org.olo.definition.planner.WorkflowPlannerMetadata;
import org.olo.definition.state.StateFieldDefinition;
import org.olo.definition.variable.VariableDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for {@link WorkflowDefinition}, including dynamic extension of existing workflows.
 */
public final class WorkflowBuilder {

    public static final String RETURN_VARIABLE_METADATA_KEY = "returnVariable";
    public static final String RETURN_VARIABLE_NAME = "ReturnValue";
    public static final String RETURN_VARIABLE_ROLE = "return";

    private final WorkflowDefinition.Builder delegate = WorkflowDefinition.builder();
    private final List<NodeDefinition> nodes = new ArrayList<>();
    private final List<EdgeDefinition> edges = new ArrayList<>();
    private final Map<String, WorkflowInputDefinition> inputs = new LinkedHashMap<>();
    private final Map<String, StateFieldDefinition> state = new LinkedHashMap<>();
    private final Map<String, WorkflowParameterDefinition> parameters = new LinkedHashMap<>();
    private final List<VariableDefinition> variables = new ArrayList<>();
    private final List<ModelProviderDefinition> modelProviders = new ArrayList<>();
    private final List<ModelRoutingDefinition> modelRouting = new ArrayList<>();
    private final List<ExtensionDefinition> extensions = new ArrayList<>();
    private final List<ToolDefinition> tools = new ArrayList<>();
    private final List<AgentDefinition> agents = new ArrayList<>();
    private final List<HookDefinition> hooks = new ArrayList<>();
    private final List<ChildWorkflowDefinition> childWorkflows = new ArrayList<>();
    private final List<AgentReferenceDefinition> availableAgents = new ArrayList<>();
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private WorkflowRuntimeDefinition.Builder workflowRuntimeBuilder;

    private WorkflowBuilder() {
    }

    /**
     * Starts a new workflow; {@code label} is the display name and seeds {@code id} when not set explicitly.
     */
    public static WorkflowBuilder create(String label) {
        Objects.requireNonNull(label, "label is required");
        WorkflowBuilder builder = new WorkflowBuilder();
        builder.delegate.label(label);
        builder.delegate.id(slugify(label));
        return builder;
    }

    /**
     * Creates a builder seeded from an existing workflow (copy-on-write for nodes, edges, and related lists).
     */
    public static WorkflowBuilder from(WorkflowDefinition existing) {
        Objects.requireNonNull(existing, "existing workflow is required");
        WorkflowBuilder builder = new WorkflowBuilder();
        builder.delegate.id(existing.getId());
        builder.delegate.label(existing.getLabel());
        builder.delegate.role(existing.getRole());
        builder.delegate.shortDescription(existing.getShortDescription());
        builder.delegate.emoji(existing.getEmoji());
        if (existing.getDesigner() != null) {
            builder.delegate.designer(existing.getDesigner());
        }
        builder.delegate.queue(existing.getQueue());
        builder.delegate.workflowType(existing.getWorkflowType());
        builder.delegate.runAgain(existing.isRunAgain());
        builder.delegate.longDescription(existing.getLongDescription());
        builder.delegate.isExternalWorkflow(existing.isExternalWorkflow());
        builder.delegate.isChildWorkflow(existing.isChildWorkflow());
        builder.childWorkflows.addAll(existing.getChildWorkflows());
        builder.availableAgents.addAll(existing.getAvailableAgents());
        builder.delegate.version(existing.getVersion());
        builder.nodes.addAll(existing.getNodes());
        builder.edges.addAll(existing.getEdges());
        builder.inputs.putAll(existing.getInputs());
        builder.state.putAll(existing.getState());
        builder.parameters.putAll(existing.getParameters());
        builder.variables.addAll(existing.getVariables());
        builder.modelProviders.addAll(existing.getModelProviders());
        builder.modelRouting.addAll(existing.getModelRouting());
        builder.extensions.addAll(existing.getExtensions());
        builder.tools.addAll(existing.getTools());
        builder.agents.addAll(existing.getAgents());
        builder.hooks.addAll(existing.getHooks());
        builder.metadata.putAll(existing.getMetadata());
        if (existing.getCapability() != null) {
            builder.delegate.capability(existing.getCapability());
        }
        if (existing.getRuntimeBinding() != null) {
            builder.delegate.runtimeBinding(existing.getRuntimeBinding());
        }
        if (existing.getRuntime() != null) {
            builder.workflowRuntimeBuilder = WorkflowRuntimeDefinition.builder()
                    .contractVersion(existing.getRuntime().getContractVersion())
                    .executionModel(existing.getRuntime().getExecutionModel())
                    .capabilities(existing.getRuntime().getCapabilities())
                    .defaultTimeout(existing.getRuntime().getDefaultTimeout())
                    .delegation(existing.getRuntime().getDelegation());
        }
        return builder;
    }

    public WorkflowBuilder id(String id) {
        delegate.id(id);
        return this;
    }

    public WorkflowBuilder label(String label) {
        delegate.label(label);
        return this;
    }

    public WorkflowBuilder role(String role) {
        delegate.role(role);
        return this;
    }

    public WorkflowBuilder shortDescription(String shortDescription) {
        delegate.shortDescription(shortDescription);
        return this;
    }

    public WorkflowBuilder emoji(String emoji) {
        delegate.emoji(emoji);
        return this;
    }

    public WorkflowBuilder queue(String queue) {
        delegate.queue(queue);
        return this;
    }

    public WorkflowBuilder workflowType(String workflowType) {
        delegate.workflowType(workflowType);
        return this;
    }

    public WorkflowBuilder runAgain(Boolean runAgain) {
        delegate.runAgain(runAgain);
        return this;
    }

    public WorkflowBuilder longDescription(String longDescription) {
        delegate.longDescription(longDescription);
        return this;
    }

    public WorkflowBuilder isExternalWorkflow(Boolean isExternalWorkflow) {
        delegate.isExternalWorkflow(isExternalWorkflow);
        return this;
    }

    public WorkflowBuilder isChildWorkflow(Boolean isChildWorkflow) {
        delegate.isChildWorkflow(isChildWorkflow);
        return this;
    }

    public WorkflowBuilder childWorkflow(ChildWorkflowDefinition childWorkflow) {
        Objects.requireNonNull(childWorkflow, "childWorkflow is required");
        childWorkflows.add(childWorkflow);
        return this;
    }

    public WorkflowBuilder childWorkflowRef(String workflowId) {
        return childWorkflow(ChildWorkflowDefinition.builder().workflowId(workflowId).build());
    }

    public WorkflowBuilder version(String version) {
        delegate.version(version);
        return this;
    }

    public WorkflowBuilder inputNode(String id) {
        return addNode(NodeDefinition.builder()
                .id(id)
                .type(NodeType.INPUT)
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT))
                .build());
    }

    public WorkflowBuilder outputNode(String id) {
        return addNode(NodeDefinition.builder()
                .id(id)
                .type(NodeType.OUTPUT)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .build());
    }

    public WorkflowBuilder modelNode(String id) {
        return modelNode(id, null);
    }

    public WorkflowBuilder modelNode(String id, String subtype) {
        NodeDefinition.Builder node = NodeDefinition.builder()
                .id(id)
                .type(NodeType.MODEL)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT));
        if (subtype != null) {
            node.subtype(subtype);
        }
        return addNode(node.build());
    }

    public WorkflowBuilder toolNode(String id) {
        return addNode(NodeDefinition.builder()
                .id(id)
                .type(NodeType.TOOL)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT))
                .build());
    }

    public WorkflowBuilder vectorSearchNode(String id) {
        return addNode(NodeDefinition.builder()
                .id(id)
                .type(NodeType.VECTOR_SEARCH)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT))
                .build());
    }

    public WorkflowBuilder agentNode(String id, WorkflowReferenceDefinition workflow) {
        return agentNode(id, null, workflow);
    }

    /**
     * Agent node: requires {@code workflow} (agent = workflow artifact). Optional subtype.
     */
    public WorkflowBuilder agentNode(String id, String subtype, WorkflowReferenceDefinition workflow) {
        Objects.requireNonNull(workflow, "workflow is required for AGENT nodes");
        NodeDefinition.Builder node = NodeDefinition.builder()
                .id(id)
                .type(NodeType.AGENT)
                .workflow(workflow)
                .executionKind(ExecutionKind.SUBWORKFLOW)
                .executionModel(ExecutionModel.CHILD_WORKFLOW)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT));
        if (subtype != null) {
            node.subtype(subtype);
        }
        return addNode(node.build());
    }

    public WorkflowBuilder humanNode(String id, HumanApprovalDefinition approval) {
        return humanNode(id, "APPROVAL", approval);
    }

    /**
     * Human-in-the-loop node with structured approval metadata (e.g. trading desk sign-off).
     */
    public WorkflowBuilder humanNode(String id, String subtype, HumanApprovalDefinition approval) {
        Objects.requireNonNull(approval, "approval is required");
        NodeDefinition.Builder node = NodeDefinition.builder()
                .id(id)
                .type(NodeType.HUMAN)
                .approval(approval)
                .addPort(defaultPort("in", "in", PortDirection.INPUT))
                .addPort(defaultPort("out", "out", PortDirection.OUTPUT));
        if (subtype != null) {
            node.subtype(subtype);
        }
        return addNode(node.build());
    }

    public WorkflowBuilder addNode(NodeDefinition node) {
        Objects.requireNonNull(node, "node is required");
        ensureUniqueNodeId(node.getId());
        nodes.add(node);
        return this;
    }

    public WorkflowBuilder addEdge(EdgeDefinition edge) {
        Objects.requireNonNull(edge, "edge is required");
        edges.add(edge);
        return this;
    }

    public WorkflowBuilder connect(String sourceNodeId, String targetNodeId) {
        return connect(sourceNodeId, null, targetNodeId, null);
    }

    public WorkflowBuilder connect(
            String sourceNodeId,
            String sourcePortId,
            String targetNodeId,
            String targetPortId) {
        return addEdge(EdgeDefinition.builder()
                .sourceNodeId(sourceNodeId)
                .sourcePortId(sourcePortId)
                .targetNodeId(targetNodeId)
                .targetPortId(targetPortId)
                .build());
    }

    public WorkflowBuilder input(String name, WorkflowInputDefinition input) {
        Objects.requireNonNull(name, "input name is required");
        Objects.requireNonNull(input, "input is required");
        inputs.put(name, input);
        return this;
    }

    public WorkflowBuilder stateField(String name, StateFieldDefinition field) {
        Objects.requireNonNull(name, "state field name is required");
        Objects.requireNonNull(field, "state field is required");
        state.put(name, field);
        return this;
    }

    public WorkflowBuilder parameter(String name, WorkflowParameterDefinition parameter) {
        Objects.requireNonNull(name, "parameter name is required");
        Objects.requireNonNull(parameter, "parameter is required");
        parameters.put(name, parameter);
        return this;
    }

    /** @deprecated use {@link #input(String, WorkflowInputDefinition)} */
    @Deprecated
    public WorkflowBuilder variable(VariableDefinition variable) {
        Objects.requireNonNull(variable, "variable is required");
        variables.add(variable);
        return this;
    }

    public WorkflowBuilder modelProvider(ModelProviderDefinition provider) {
        Objects.requireNonNull(provider, "provider is required");
        modelProviders.add(provider);
        return this;
    }

    public WorkflowBuilder modelRouting(ModelRoutingDefinition routing) {
        Objects.requireNonNull(routing, "routing is required");
        modelRouting.add(routing);
        return this;
    }

    public WorkflowBuilder extension(ExtensionDefinition extension) {
        Objects.requireNonNull(extension, "extension is required");
        extensions.add(extension);
        return this;
    }

    public WorkflowBuilder metadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }

    public WorkflowBuilder metadata(Map<String, Object> metadata) {
        this.metadata.putAll(metadata);
        return this;
    }

    /**
     * Declares the standard workflow return variable and {@code metadata.returnVariable = ReturnValue}.
     */
    public WorkflowBuilder withStandardReturnVariable() {
        metadata.putIfAbsent(RETURN_VARIABLE_METADATA_KEY, RETURN_VARIABLE_NAME);
        boolean hasReturnVariable = variables.stream()
                .anyMatch(variable -> RETURN_VARIABLE_NAME.equals(variable.getName()));
        if (!hasReturnVariable) {
            variable(VariableDefinition.builder()
                    .name(RETURN_VARIABLE_NAME)
                    .type("string")
                    .description("Workflow return message returned to the caller")
                    .required(false)
                    .defaultValue(null)
                    .metadata(Map.of("role", RETURN_VARIABLE_ROLE))
                    .build());
        }
        return this;
    }

    /**
     * Planner-readable contract for this workflow (required for valid workflows).
     */
    public WorkflowBuilder capability(CapabilityDefinition capability) {
        Objects.requireNonNull(capability, "capability is required");
        delegate.capability(capability);
        return this;
    }

    public WorkflowBuilder runtime(WorkflowRuntimeDefinition runtime) {
        delegate.runtime(runtime);
        this.workflowRuntimeBuilder = null;
        return this;
    }

    /**
     * Sets {@code runtime.executionModel} (catalog-aligned: {@code INLINE}, {@code ACTIVITY},
     * {@code CHILD_WORKFLOW}, {@code EXTERNAL}).
     */
    public WorkflowBuilder executionModel(ExecutionModel executionModel) {
        workflowRuntimeBuilder().executionModel(executionModel);
        return this;
    }

    public WorkflowBuilder defaultTimeout(String defaultTimeout) {
        workflowRuntimeBuilder().defaultTimeout(defaultTimeout);
        return this;
    }

    /** {@code CHILD_WORKFLOW} agent runtime with debug, replay, timeout, and a 10-minute default. */
    public WorkflowBuilder agentWorkflowRuntime() {
        return executionModel(ExecutionModel.CHILD_WORKFLOW)
                .debuggable()
                .replayable()
                .addRuntimeCapability(RuntimeCapability.TIMEOUT)
                .defaultTimeout("PT10M");
    }

    /** Agent tuning parameters ({@link AgentWorkflowParameters}). */
    public WorkflowBuilder agentParameters() {
        AgentWorkflowParameters.defaults().forEach(this::parameter);
        return this;
    }

    /** Planner routing metadata ({@link WorkflowPlannerMetadata}). */
    public WorkflowBuilder agentPlannerMetadata() {
        WorkflowPlannerMetadata.agentDefaults().forEach(this::metadata);
        return this;
    }

    public WorkflowBuilder availableAgent(String agentWorkflowId) {
        return availableAgent(AgentReferenceDefinition.of(agentWorkflowId));
    }

    public WorkflowBuilder availableAgent(AgentReferenceDefinition agentReference) {
        availableAgents.add(agentReference);
        return this;
    }

    /** Default {@code availableAgents} for the agent preset ({@link AgentAvailableAgents}). */
    public WorkflowBuilder agentAvailableAgents() {
        AgentAvailableAgents.agentPresetDefaults().forEach(this::availableAgent);
        return this;
    }

    public WorkflowBuilder delegation(RuntimeDelegationDefinition delegation) {
        workflowRuntimeBuilder().delegation(delegation);
        return this;
    }

    /** Delegation guardrails for the agent preset ({@link AgentDelegationPolicy}). */
    public WorkflowBuilder agentDelegation() {
        return delegation(AgentDelegationPolicy.agentPresetDefaults());
    }

    public WorkflowBuilder debuggable() {
        workflowRuntimeBuilder().debuggable(true);
        return this;
    }

    public WorkflowBuilder replayable() {
        workflowRuntimeBuilder().replayable(true);
        return this;
    }

    public WorkflowBuilder addRuntimeCapability(RuntimeCapability capability) {
        workflowRuntimeBuilder().addCapability(capability);
        return this;
    }

    public WorkflowBuilder runtimeBinding(RuntimeBindingDefinition runtimeBinding) {
        delegate.runtimeBinding(runtimeBinding);
        return this;
    }

    public WorkflowBuilder tool(ToolDefinition tool) {
        Objects.requireNonNull(tool, "tool is required");
        tools.add(tool);
        return this;
    }

    public WorkflowBuilder agent(AgentDefinition agent) {
        Objects.requireNonNull(agent, "agent is required");
        agents.add(agent);
        return this;
    }

    public WorkflowBuilder hook(HookDefinition hook) {
        Objects.requireNonNull(hook, "hook is required");
        hooks.add(hook);
        return this;
    }

    public WorkflowBuilder designer(DesignerDefinition designer) {
        delegate.designer(designer);
        return this;
    }

    public WorkflowDefinition build() {
        delegate.nodes(List.copyOf(nodes));
        delegate.edges(List.copyOf(edges));
        delegate.inputs(Map.copyOf(inputs));
        delegate.state(Map.copyOf(state));
        delegate.parameters(Map.copyOf(parameters));
        delegate.variables(List.copyOf(variables));
        delegate.modelProviders(List.copyOf(modelProviders));
        delegate.modelRouting(List.copyOf(modelRouting));
        delegate.extensions(List.copyOf(extensions));
        delegate.tools(List.copyOf(tools));
        delegate.agents(List.copyOf(agents));
        delegate.hooks(List.copyOf(hooks));
        delegate.childWorkflows(List.copyOf(childWorkflows));
        delegate.availableAgents(List.copyOf(availableAgents));
        delegate.metadata(Map.copyOf(metadata));
        if (workflowRuntimeBuilder != null) {
            delegate.runtime(workflowRuntimeBuilder.build());
        }
        return delegate.build();
    }

    private WorkflowRuntimeDefinition.Builder workflowRuntimeBuilder() {
        if (workflowRuntimeBuilder == null) {
            workflowRuntimeBuilder = WorkflowRuntimeDefinition.builder();
        }
        return workflowRuntimeBuilder;
    }

    private void ensureUniqueNodeId(String id) {
        for (NodeDefinition existing : nodes) {
            if (existing.getId().equals(id)) {
                throw new IllegalArgumentException("duplicate node id: " + id);
            }
        }
    }

    private static String slugify(String name) {
        return name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static PortDefinition defaultPort(String id, String name, PortDirection direction) {
        return PortDefinition.builder()
                .id(id)
                .name(name)
                .schema("any")
                .direction(direction)
                .ui(PortUiDefinition.forDirection(direction))
                .build();
    }
}
