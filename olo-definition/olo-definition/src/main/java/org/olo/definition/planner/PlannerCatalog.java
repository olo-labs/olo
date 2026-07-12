/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.planner;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.olo.definition.agent.AgentDefinition;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.tool.ToolDefinition;
import org.olo.definition.workflow.WorkflowDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated planner-facing catalog. Built from capability metadata only — never from
 * {@code nodes}, {@code edges}, or {@code configuration}.
 * <p>
 * V1 includes workflows, agents, and tools only. {@link CatalogKind#NODE} entries are intentionally
 * omitted to avoid duplicate catalog rows alongside agent/tool/workflow capabilities.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PlannerCatalog {

    private final List<PlannerCapability> workflows;
    private final List<PlannerCapability> agents;
    private final List<PlannerCapability> tools;
    private final List<PlannerCapability> nodes;

    private PlannerCatalog(
            List<PlannerCapability> workflows,
            List<PlannerCapability> agents,
            List<PlannerCapability> tools,
            List<PlannerCapability> nodes) {
        this.workflows = workflows == null ? List.of() : List.copyOf(workflows);
        this.agents = agents == null ? List.of() : List.copyOf(agents);
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public static PlannerCatalog from(WorkflowDefinition workflow) {
        Objects.requireNonNull(workflow, "workflow is required");

        List<PlannerCapability> workflowEntries = new ArrayList<>();
        CapabilityDefinition workflowCapability = workflow.getCapability();
        if (workflowCapability != null) {
            workflowEntries.add(new PlannerCapability(workflow.getId(), CatalogKind.WORKFLOW, workflowCapability));
        }

        List<PlannerCapability> agentEntries = new ArrayList<>();
        for (AgentDefinition agent : workflow.getAgents()) {
            if (agent != null && agent.getCapability() != null) {
                agentEntries.add(new PlannerCapability(agent.getId(), CatalogKind.AGENT, agent.getCapability()));
            }
        }

        List<PlannerCapability> toolEntries = new ArrayList<>();
        for (ToolDefinition tool : workflow.getTools()) {
            if (tool != null && tool.getCapability() != null) {
                toolEntries.add(new PlannerCapability(tool.getId(), CatalogKind.TOOL, tool.getCapability()));
            }
        }

        return new PlannerCatalog(workflowEntries, agentEntries, toolEntries, List.of());
    }

    public List<PlannerCapability> getWorkflows() {
        return workflows;
    }

    public List<PlannerCapability> getAgents() {
        return agents;
    }

    public List<PlannerCapability> getTools() {
        return tools;
    }

    /**
     * Always empty in V1 — node-level capabilities are not exposed to planners.
     */
    public List<PlannerCapability> getNodes() {
        return nodes;
    }

    /** Flat list in planner-friendly order: workflows, agents, tools. */
    public List<PlannerCapability> getEntries() {
        List<PlannerCapability> all = new ArrayList<>();
        all.addAll(workflows);
        all.addAll(agents);
        all.addAll(tools);
        return Collections.unmodifiableList(all);
    }

    /**
     * Full planner catalog for LLM routing — {@link PlannerCapability} entries with cost, latency,
     * confidence, required inputs/context, and tool requirements. Never includes graph or config.
     */
    public List<PlannerCapability> plannerView() {
        return getEntries();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlannerCatalog that)) {
            return false;
        }
        return Objects.equals(workflows, that.workflows)
                && Objects.equals(agents, that.agents)
                && Objects.equals(tools, that.tools)
                && Objects.equals(nodes, that.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflows, agents, tools, nodes);
    }
}
