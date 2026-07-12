/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.planner;

/**
 * Kind of planner catalog entry (workflow, agent, tool, or node-level capability).
 */
public enum CatalogKind {
    WORKFLOW,
    AGENT,
    TOOL,
    NODE
}
