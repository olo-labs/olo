/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.execution;

/**
 * How a node is executed at runtime—not the business role of the node.
 * <p>
 * {@link org.olo.definition.node.NodeDefinition#getType()} describes <em>what</em> the step is
 * (MODEL, AGENT, HUMAN, …). {@code executionKind} describes <em>how</em> the runtime schedules it.
 * Business abstractions such as {@code AGENT} must not appear here.
 */
public enum ExecutionKind {

    /** Single schedulable unit (LLM call, tool, planner step, branch logic, etc.). */
    ACTIVITY,
    /** Top-level or nested workflow boundary. */
    WORKFLOW,
    /** Child workflow (Temporal-style); used for {@code AGENT} / {@code WORKFLOW_REF} nodes. */
    SUBWORKFLOW,
    /** Blocks on external human signal (approval, input) before continuing. */
    HUMAN_WAIT,
    /** Event / signal boundary (timer, message, external trigger). */
    EVENT
}
