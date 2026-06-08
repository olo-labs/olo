package org.olo.definition.validation;

import org.olo.definition.edge.EdgeDefinition;
import org.olo.definition.error.ErrorRoute;
import org.olo.definition.error.OnFailureDefinition;
import org.olo.definition.error.RetryPolicy;
import org.olo.definition.human.HumanApprovalDefinition;
import org.olo.definition.execution.ExecutionKind;
import org.olo.definition.node.NodeType;
import org.olo.definition.parallel.JoinDefinition;
import org.olo.definition.parallel.JoinStrategy;
import org.olo.definition.workflow.ChildWorkflowDefinition;
import org.olo.definition.workflow.WorkflowReferenceDefinition;
import org.olo.definition.extension.ExtensionDefinition;
import org.olo.definition.hook.HookCatalog;
import org.olo.definition.hook.HookDefinition;
import org.olo.definition.hook.HookValidator;
import org.olo.definition.model.ModelProviderDefinition;
import org.olo.definition.model.ModelRoutingDefinition;
import org.olo.definition.node.NodeDefinition;
import org.olo.definition.node.NodeRouterDefinition;
import org.olo.definition.port.PortDefinition;
import org.olo.definition.port.PortDirection;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.path.DataPath;
import org.olo.definition.path.DataPathParseResult;
import org.olo.definition.path.DataPathParser;
import org.olo.definition.path.PathRoot;
import org.olo.definition.state.EffectiveStateFields;
import org.olo.definition.state.StateFieldDefinition;
import org.olo.definition.variable.VariableDefinition;
import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.capability.CapabilityValidator;
import org.olo.definition.runtime.RuntimeBindingValidator;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.workflow.WorkflowDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural validation for workflow definitions (no runtime or execution checks).
 */
public final class WorkflowValidator {

    private WorkflowValidator() {
    }

    public static ValidationResult validate(WorkflowDefinition workflow) {
        List<String> errors = new ArrayList<>();
        if (workflow == null) {
            return ValidationResult.failure(List.of("workflow must not be null"));
        }
        if (isBlank(workflow.getId())) {
            errors.add("workflow id is required");
        }
        validateCapability(workflow, errors);
        validateChildWorkflows(workflow, errors);
        RuntimeBindingValidator.validate("workflow " + workflow.getId(), workflow.getRuntimeBinding(), errors);

        Set<String> hookImplementationIds = HookCatalog.implementationIds(workflow);

        Set<String> nodeIds = new HashSet<>();
        Map<String, NodeDefinition> nodesById = new HashMap<>();
        for (NodeDefinition node : workflow.getNodes()) {
            if (node == null) {
                errors.add("node entry must not be null");
                continue;
            }
            if (isBlank(node.getId())) {
                errors.add("node id is required");
            } else if (!nodeIds.add(node.getId())) {
                errors.add("duplicate node id: " + node.getId());
            } else {
                nodesById.put(node.getId(), node);
            }
            if (isBlank(node.getType())) {
                errors.add("node type is required for node: " + node.getId());
            }
            if (!isBlank(node.getId())) {
                validatePorts(node, errors);
                validateNodeCapability(node, errors);
                validateNodeRuntimeBinding(node, errors);
                HookValidator.validateNodeHooks(
                        "workflow " + workflow.getId(),
                        node.getId(),
                        node.getHooks(),
                        hookImplementationIds,
                        errors);
            }
        }
        Set<String> stateFieldNames = EffectiveStateFields.names(workflow);
        Set<String> inputFieldNames = new HashSet<>(workflow.getInputs().keySet());
        Set<String> parameterFieldNames = new HashSet<>(workflow.getParameters().keySet());
        EffectiveStateFields.validateDeclarations(workflow, errors);

        for (NodeDefinition node : workflow.getNodes()) {
            if (node != null) {
                validateNodeRouters(node, nodeIds, errors);
                validateOnFailure(node, nodeIds, errors);
                validateHumanApproval(node, errors);
                validateExecutionMapping(node, errors);
                validateWorkflowReference(node, errors);
                validateParallelJoin(node, errors);
                validateDataPathAccess(node, stateFieldNames, inputFieldNames, parameterFieldNames, errors);
            }
        }

        for (Map.Entry<String, WorkflowInputDefinition> entry : workflow.getInputs().entrySet()) {
            if (isBlank(entry.getKey())) {
                errors.add("input name is required");
            } else if (entry.getValue() == null) {
                errors.add("input definition must not be null for: " + entry.getKey());
            }
        }

        for (Map.Entry<String, StateFieldDefinition> entry : workflow.getState().entrySet()) {
            if (isBlank(entry.getKey())) {
                errors.add("state field name is required");
            } else if (entry.getValue() == null) {
                errors.add("state field definition must not be null for: " + entry.getKey());
            }
        }

        Set<String> variableNames = new HashSet<>();
        for (VariableDefinition variable : workflow.getVariables()) {
            if (variable == null) {
                errors.add("variable entry must not be null");
                continue;
            }
            if (isBlank(variable.getName())) {
                errors.add("variable name is required");
            } else if (!variableNames.add(variable.getName())) {
                errors.add("duplicate variable name: " + variable.getName());
            }
        }

        Set<String> providerIds = new HashSet<>();
        for (ModelProviderDefinition provider : workflow.getModelProviders()) {
            if (provider == null) {
                errors.add("model provider entry must not be null");
                continue;
            }
            if (isBlank(provider.getId())) {
                errors.add("model provider id is required");
            } else if (!providerIds.add(provider.getId())) {
                errors.add("duplicate model provider id: " + provider.getId());
            }
        }

        for (ModelRoutingDefinition routing : workflow.getModelRouting()) {
            if (routing != null && !isBlank(routing.getDefaultProviderId())) {
                String defaultId = routing.getDefaultProviderId();
                if (!providerIds.isEmpty() && !providerIds.contains(defaultId)) {
                    errors.add("model routing references unknown provider: " + defaultId);
                }
            }
        }

        Set<String> registryIds = new HashSet<>();
        for (ToolDefinition tool : workflow.getTools()) {
            if (tool == null) {
                errors.add("tool entry must not be null");
                continue;
            }
            if (isBlank(tool.getId())) {
                errors.add("tool id is required");
            } else if (!registryIds.add(tool.getId())) {
                errors.add("duplicate registry id: " + tool.getId());
            } else {
                validateTool(tool, errors);
            }
        }
        for (AgentDefinition agent : workflow.getAgents()) {
            if (agent == null) {
                errors.add("agent entry must not be null");
                continue;
            }
            if (isBlank(agent.getId())) {
                errors.add("agent id is required");
            } else if (!registryIds.add(agent.getId())) {
                errors.add("duplicate registry id: " + agent.getId());
            } else {
                validateAgent(agent, errors);
            }
        }

        Set<String> hookIds = new HashSet<>();
        for (HookDefinition hook : workflow.getHooks()) {
            if (hook == null) {
                errors.add("hook entry must not be null");
                continue;
            }
            if (isBlank(hook.getId())) {
                HookValidator.validate("workflow " + workflow.getId(), hook, errors);
            } else if (!hookIds.add(hook.getId())) {
                errors.add("duplicate hook id: " + hook.getId());
            } else {
                HookValidator.validate("workflow " + workflow.getId(), hook, errors);
            }
        }

        Set<String> extensionIds = new HashSet<>();
        for (ExtensionDefinition extension : workflow.getExtensions()) {
            if (extension == null) {
                errors.add("extension entry must not be null");
                continue;
            }
            if (isBlank(extension.getId())) {
                errors.add("extension id is required");
            } else if (!extensionIds.add(extension.getId())) {
                errors.add("duplicate extension id: " + extension.getId());
            }
        }

        int edgeIndex = 0;
        Map<String, Map<String, Integer>> outgoingCounts = new HashMap<>();
        Map<String, Map<String, Integer>> incomingCounts = new HashMap<>();
        for (EdgeDefinition edge : workflow.getEdges()) {
            if (edge == null) {
                errors.add("edge entry must not be null");
                edgeIndex++;
                continue;
            }
            String prefix = "edge[" + edgeIndex + "]: ";
            if (isBlank(edge.getSourceNodeId())) {
                errors.add(prefix + "sourceNodeId is required");
            } else if (!nodeIds.contains(edge.getSourceNodeId())) {
                errors.add(prefix + "unknown source node: " + edge.getSourceNodeId());
            }
            if (isBlank(edge.getTargetNodeId())) {
                errors.add(prefix + "targetNodeId is required");
            } else if (!nodeIds.contains(edge.getTargetNodeId())) {
                errors.add(prefix + "unknown target node: " + edge.getTargetNodeId());
            }
            if (edge.getSourceNodeId() != null
                    && edge.getSourceNodeId().equals(edge.getTargetNodeId())) {
                errors.add(prefix + "self-loop on node: " + edge.getSourceNodeId());
            }
            EdgePortValidation edgePorts = validateEdgePorts(edge, nodesById, prefix, errors);
            if (edgePorts.source().port != null && edge.getSourceNodeId() != null) {
                incrementCount(outgoingCounts, edge.getSourceNodeId(), edgePorts.source().port.getId());
            }
            if (edgePorts.target().port != null && edge.getTargetNodeId() != null) {
                incrementCount(incomingCounts, edge.getTargetNodeId(), edgePorts.target().port.getId());
            }
            edgeIndex++;
        }

        for (NodeDefinition node : workflow.getNodes()) {
            if (node != null) {
                validatePortConnectionCounts(node, outgoingCounts, incomingCounts, errors);
            }
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failure(errors);
    }

    public static void validateOrThrow(WorkflowDefinition workflow) {
        ValidationResult result = validate(workflow);
        if (!result.valid()) {
            throw new WorkflowValidationException(result.errors());
        }
    }

    private static void validateHumanApproval(NodeDefinition node, List<String> errors) {
        String nodeId = node.getId();
        boolean isHuman = NodeType.HUMAN.value().equals(node.getType());
        HumanApprovalDefinition approval = node.getApproval();
        if (approval != null && !isHuman) {
            errors.add("approval is only valid on HUMAN nodes, found on: " + nodeId);
            return;
        }
        if (!isHuman) {
            return;
        }
        if (approval == null) {
            errors.add("HUMAN node " + nodeId + " requires an approval block");
            return;
        }
        if (isBlank(approval.getTitle())) {
            errors.add("approval title is required on HUMAN node: " + nodeId);
        }
        if (approval.getApprovers().isEmpty()) {
            errors.add("approval approvers must not be empty on HUMAN node: " + nodeId);
        }
        if (approval.getTimeoutSeconds() != null && approval.getTimeoutSeconds() < 0) {
            errors.add("approval timeoutSeconds must be >= 0 on HUMAN node: " + nodeId);
        }
    }

    private static void validateWorkflowReference(NodeDefinition node, List<String> errors) {
        String nodeId = node.getId();
        String type = node.getType();
        WorkflowReferenceDefinition workflow = node.getWorkflow();

        if (NodeType.AGENT.value().equals(type) && workflow == null) {
            errors.add("AGENT node " + nodeId + " requires a workflow reference (agent = workflow)");
        }
        if (NodeType.WORKFLOW_REF.value().equals(type) && workflow == null) {
            errors.add("WORKFLOW_REF node " + nodeId + " requires a workflow reference");
        }
        if (workflow != null
                && !NodeType.WORKFLOW_REF.value().equals(type)
                && !NodeType.AGENT.value().equals(type)) {
            errors.add("workflow is only valid on AGENT or WORKFLOW_REF nodes, found on: " + nodeId);
        }
        if ("SUBGRAPH".equals(type) || "SUBWORKFLOW".equals(type)) {
            errors.add(
                    "type "
                            + type
                            + " on node "
                            + nodeId
                            + " is not supported; use WORKFLOW_REF with workflow instead");
        }
    }

    private static void validateParallelJoin(NodeDefinition node, List<String> errors) {
        String nodeId = node.getId();
        JoinDefinition join = node.getJoin();
        if (NodeType.PARALLEL.value().equals(node.getType())) {
            if (join == null) {
                errors.add("PARALLEL node " + nodeId + " requires a join definition");
                return;
            }
            if (join.getStrategy() == null) {
                errors.add("join strategy is required on PARALLEL node: " + nodeId);
            }
            if (join.getStrategy() == JoinStrategy.QUORUM
                    && (join.getQuorumCount() == null || join.getQuorumCount() < 1)) {
                errors.add("join quorumCount must be >= 1 when strategy is QUORUM on node: " + nodeId);
            }
        } else if (join != null) {
            errors.add("join is only valid on PARALLEL nodes, found on: " + nodeId);
        }
    }

    private static void validateExecutionMapping(NodeDefinition node, List<String> errors) {
        String nodeId = node.getId();
        String type = node.getType();
        ExecutionKind kind = node.getExecutionKind();
        if (kind == ExecutionKind.HUMAN_WAIT && !NodeType.HUMAN.value().equals(type)) {
            errors.add("executionKind HUMAN_WAIT requires type HUMAN on node: " + nodeId);
        }
        if (NodeType.HUMAN.value().equals(type)
                && kind != null
                && kind != ExecutionKind.HUMAN_WAIT
                && kind != ExecutionKind.EVENT) {
            errors.add("executionKind on HUMAN node " + nodeId + " should be HUMAN_WAIT or EVENT");
        }
    }

    private static void validateOnFailure(NodeDefinition node, Set<String> nodeIds, List<String> errors) {
        OnFailureDefinition onFailure = node.getOnFailure();
        if (onFailure == null) {
            return;
        }
        String nodeId = node.getId();
        if (onFailure.getRetry() == null && onFailure.getRoute() == null) {
            errors.add("onFailure on node " + nodeId + " must declare retry and/or route");
            return;
        }
        RetryPolicy retry = onFailure.getRetry();
        if (retry != null) {
            if (retry.getAttempts() < 1) {
                errors.add("onFailure retry attempts must be >= 1 on node: " + nodeId);
            }
            if (retry.getInitialDelayMs() != null && retry.getInitialDelayMs() < 0) {
                errors.add("onFailure retry initialDelayMs must be >= 0 on node: " + nodeId);
            }
            if (retry.getMaxDelayMs() != null && retry.getMaxDelayMs() < 0) {
                errors.add("onFailure retry maxDelayMs must be >= 0 on node: " + nodeId);
            }
        }
        ErrorRoute route = onFailure.getRoute();
        if (route != null) {
            if (isBlank(route.getTargetNodeId())) {
                errors.add("onFailure route targetNodeId is required on node: " + nodeId);
            } else if (!nodeIds.contains(route.getTargetNodeId())) {
                errors.add(
                        "onFailure route on node "
                                + nodeId
                                + " references unknown target node: "
                                + route.getTargetNodeId());
            } else if (route.getTargetNodeId().equals(nodeId)) {
                errors.add("onFailure route on node " + nodeId + " must not target the same node");
            }
        }
    }

    private static void validateNodeRouters(
            NodeDefinition node, Set<String> nodeIds, List<String> errors) {
        Set<String> routerIds = new HashSet<>();
        for (NodeRouterDefinition router : node.getRouters()) {
            if (router == null) {
                errors.add("router entry must not be null on node: " + node.getId());
                continue;
            }
            if (!isBlank(router.getId()) && !routerIds.add(router.getId())) {
                errors.add("duplicate router id '" + router.getId() + "' on node: " + node.getId());
            }
            if (!isBlank(router.getTargetNodeId()) && !nodeIds.contains(router.getTargetNodeId())) {
                errors.add(
                        "router on node "
                                + node.getId()
                                + " references unknown target node: "
                                + router.getTargetNodeId());
            }
        }
    }

    private static void validatePorts(NodeDefinition node, List<String> errors) {
        if (node.getPorts().isEmpty()) {
            errors.add("node " + node.getId() + " requires at least one port");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (PortDefinition port : node.getPorts()) {
            if (port == null) {
                errors.add("null port on node: " + node.getId());
                continue;
            }
            if (isBlank(port.getId())) {
                errors.add("port id is required on node: " + node.getId());
            } else if (!ids.add(port.getId())) {
                errors.add("duplicate port id '" + port.getId() + "' on node: " + node.getId());
            }
            if (isBlank(port.getName())) {
                errors.add("port name is required on node: " + node.getId());
            }
            if (port.getDirection() == null) {
                errors.add("port direction is required on node " + node.getId() + " port: " + port.getId());
            }
            if (isBlank(port.getSchema())) {
                errors.add("port '" + port.getId() + "' on node " + node.getId() + " requires a schema");
            }
            if (port.getMinConnections() < 0) {
                errors.add("port '" + port.getId() + "' on node " + node.getId() + " minConnections must be >= 0");
            }
            if (port.getMaxConnections() != null && port.getMaxConnections() < port.getMinConnections()) {
                errors.add("port '" + port.getId() + "' on node " + node.getId() + " maxConnections must be >= minConnections");
            }
        }
    }

    private static void validatePortConnectionCounts(
            NodeDefinition node,
            Map<String, Map<String, Integer>> outgoingCounts,
            Map<String, Map<String, Integer>> incomingCounts,
            List<String> errors) {
        for (PortDefinition port : node.getPorts()) {
            if (port == null || isBlank(port.getId())) {
                continue;
            }
            int count = port.getDirection() == PortDirection.OUTPUT
                    ? outgoingCounts.getOrDefault(node.getId(), Map.of()).getOrDefault(port.getId(), 0)
                    : incomingCounts.getOrDefault(node.getId(), Map.of()).getOrDefault(port.getId(), 0);
            if (port.isRequired() && count == 0) {
                errors.add("required port '" + port.getId() + "' on node " + node.getId() + " has no connections");
            }
            if (count < port.getMinConnections()) {
                errors.add(
                        "port '"
                                + port.getId()
                                + "' on node "
                                + node.getId()
                                + " has "
                                + count
                                + " connections but requires at least "
                                + port.getMinConnections());
            }
            if (port.getMaxConnections() != null && count > port.getMaxConnections()) {
                errors.add(
                        "port '"
                                + port.getId()
                                + "' on node "
                                + node.getId()
                                + " has "
                                + count
                                + " connections but allows at most "
                                + port.getMaxConnections());
            }
        }
    }

    private static void incrementCount(Map<String, Map<String, Integer>> counts, String nodeId, String portId) {
        counts.computeIfAbsent(nodeId, ignored -> new HashMap<>())
                .merge(portId, 1, Integer::sum);
    }

    private static EdgePortValidation validateEdgePorts(
            EdgeDefinition edge,
            Map<String, NodeDefinition> nodesById,
            String prefix,
            List<String> errors) {
        NodeDefinition source = nodesById.get(edge.getSourceNodeId());
        NodeDefinition target = nodesById.get(edge.getTargetNodeId());
        if (source == null) {
            return new EdgePortValidation(
                    PortResolution.unresolved(edge.getSourcePortId()),
                    PortResolution.unresolved(edge.getTargetPortId()));
        }

        PortResolution sourcePort = resolvePort(
                edge.getSourcePortId(),
                outputPorts(source),
                PortDirection.OUTPUT,
                "output",
                source.getId(),
                prefix,
                errors);

        if (target == null) {
            return new EdgePortValidation(sourcePort, PortResolution.unresolved(edge.getTargetPortId()));
        }

        PortResolution targetPort = resolvePort(
                edge.getTargetPortId(),
                inputPorts(target),
                PortDirection.INPUT,
                "input",
                target.getId(),
                prefix,
                errors);

        if (sourcePort.port != null && targetPort.port != null) {
            if (!SchemaCompatibility.compatible(sourcePort.port.getSchema(), targetPort.port.getSchema())) {
                errors.add(
                        prefix
                                + "schema mismatch: output port '"
                                + sourcePort.port.getId()
                                + "' on node "
                                + source.getId()
                                + " ("
                                + sourcePort.port.getSchema()
                                + ") is not compatible with input port '"
                                + targetPort.port.getId()
                                + "' on node "
                                + target.getId()
                                + " ("
                                + targetPort.port.getSchema()
                                + ")");
            }
        }
        return new EdgePortValidation(sourcePort, targetPort);
    }

    private record EdgePortValidation(PortResolution source, PortResolution target) {
    }

    private static List<PortDefinition> inputPorts(NodeDefinition node) {
        return node.getPorts().stream()
                .filter(port -> port.getDirection() == PortDirection.INPUT)
                .toList();
    }

    private static List<PortDefinition> outputPorts(NodeDefinition node) {
        return node.getPorts().stream()
                .filter(port -> port.getDirection() == PortDirection.OUTPUT)
                .toList();
    }

    private static PortResolution resolvePort(
            String portId,
            List<PortDefinition> ports,
            PortDirection expectedDirection,
            String kind,
            String nodeId,
            String prefix,
            List<String> errors) {
        if (ports.isEmpty()) {
            return PortResolution.unresolved(portId);
        }

        String resolvedId = portId;
        if (isBlank(resolvedId)) {
            if (ports.size() == 1) {
                resolvedId = ports.get(0).getId();
            } else {
                errors.add(
                        prefix
                                + "sourcePortId/targetPortId is required on node "
                                + nodeId
                                + " (declares "
                                + ports.size()
                                + " "
                                + kind
                                + " ports)");
                return PortResolution.unresolved(null);
            }
        }

        for (PortDefinition port : ports) {
            if (port != null && resolvedId.equals(port.getId())) {
                if (port.getDirection() != expectedDirection) {
                    errors.add(
                            prefix
                                    + "port '"
                                    + resolvedId
                                    + "' on node "
                                    + nodeId
                                    + " must be "
                                    + expectedDirection.value());
                }
                return new PortResolution(resolvedId, port);
            }
        }
        errors.add(
                prefix
                        + "unknown "
                        + kind
                        + " port '"
                        + resolvedId
                        + "' on node "
                        + nodeId);
        return PortResolution.unresolved(resolvedId);
    }

    private record PortResolution(String id, PortDefinition port) {

        static PortResolution unresolved(String id) {
            return new PortResolution(id, null);
        }
    }

    private static void validateChildWorkflows(WorkflowDefinition workflow, List<String> errors) {
        Set<String> childIds = new HashSet<>();
        for (ChildWorkflowDefinition child : workflow.getChildWorkflows()) {
            if (child == null) {
                errors.add("child workflow entry must not be null");
                continue;
            }
            if (isBlank(child.getQueue())) {
                errors.add("child workflow queue is required");
            } else if (!childIds.add(child.getQueue())) {
                errors.add("duplicate child workflow queue: " + child.getQueue());
            }
        }
    }

    private static void validateCapability(WorkflowDefinition workflow, List<String> errors) {
        CapabilityValidator.validate(
                workflow.getId(),
                workflow.getCapability(),
                CapabilityValidator.Context.WORKFLOW,
                errors);
    }

    private static void validateTool(ToolDefinition tool, List<String> errors) {
        if (!ToolDefinition.TYPE.equals(tool.getType())) {
            errors.add("tool " + tool.getId() + ": type must be " + ToolDefinition.TYPE);
        }
        CapabilityValidator.validate(tool.getId(), tool.getCapability(), CapabilityValidator.Context.TOOL, errors);
        RuntimeBindingValidator.validate("tool " + tool.getId(), tool.getRuntimeBinding(), errors);
    }

    private static void validateAgent(AgentDefinition agent, List<String> errors) {
        if (!AgentDefinition.TYPE.equals(agent.getType())) {
            errors.add("agent " + agent.getId() + ": type must be " + AgentDefinition.TYPE);
        }
        CapabilityValidator.validate(agent.getId(), agent.getCapability(), CapabilityValidator.Context.AGENT, errors);
        if (agent.getWorkflow() == null) {
            errors.add("agent " + agent.getId() + ": workflow reference is required");
        } else if (isBlank(agent.getWorkflow().getWorkflowId())) {
            errors.add("agent " + agent.getId() + ": workflow.workflowId is required");
        }
        RuntimeBindingValidator.validate("agent " + agent.getId(), agent.getRuntimeBinding(), errors);
    }

    private static void validateNodeRuntimeBinding(NodeDefinition node, List<String> errors) {
        if (node.getRuntimeBinding() == null) {
            return;
        }
        String type = node.getType();
        boolean allowed =
                NodeType.AGENT.value().equals(type)
                        || NodeType.WORKFLOW_REF.value().equals(type)
                        || NodeType.TOOL.value().equals(type);
        if (!allowed) {
            errors.add("runtimeBinding is only valid on AGENT, WORKFLOW_REF, or TOOL nodes, found on: " + node.getId());
            return;
        }
        RuntimeBindingValidator.validate("node " + node.getId(), node.getRuntimeBinding(), errors);
    }

    private static void validateNodeCapability(NodeDefinition node, List<String> errors) {
        if (node.getCapability() != null) {
            CapabilityValidator.validate(
                    node.getId(), node.getCapability(), CapabilityValidator.Context.NODE, errors);
        }
    }

    private static void validateDataPathAccess(
            NodeDefinition node,
            Set<String> stateFieldNames,
            Set<String> inputFieldNames,
            Set<String> parameterFieldNames,
            List<String> errors) {
        String nodeId = node.getId();
        for (String read : node.getReads()) {
            validateDataPath(nodeId, "read", read, false, stateFieldNames, inputFieldNames, parameterFieldNames, errors);
        }
        for (String write : node.getWrites()) {
            validateDataPath(nodeId, "write", write, true, stateFieldNames, inputFieldNames, parameterFieldNames, errors);
        }
    }

    private static void validateDataPath(
            String nodeId,
            String accessKind,
            String pathLiteral,
            boolean write,
            Set<String> stateFieldNames,
            Set<String> inputFieldNames,
            Set<String> parameterFieldNames,
            List<String> errors) {
        DataPathParseResult parsed = DataPathParser.parse(pathLiteral);
        if (!parsed.isSuccess()) {
            errors.add("node " + nodeId + ": invalid " + accessKind + " path '" + pathLiteral + "': "
                    + parsed.error().orElse("parse error"));
            return;
        }
        DataPath path = parsed.path().orElseThrow();
        if (write && path.root() != PathRoot.STATE) {
            errors.add("node " + nodeId + ": write path must use state. namespace, found: " + path.literal());
            return;
        }
        String topLevel = path.topLevelName();
        boolean declared = switch (path.root()) {
            case STATE -> stateFieldNames.contains(topLevel);
            case INPUT -> inputFieldNames.contains(topLevel);
            case PARAMETER -> parameterFieldNames.contains(topLevel);
        };
        if (!declared) {
            errors.add("node " + nodeId + " " + accessKind + "s unknown " + path.root().prefix() + " field: "
                    + path.literal() + " (no " + path.root().prefix() + " field '" + topLevel + "')");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
