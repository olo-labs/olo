/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.hook;

import org.olo.definition.workflow.WorkflowDefinition;

import java.util.HashSet;
import java.util.Set;

/**
 * Collects implementation ids declared on workflow-level hooks (the catalog nodes may reference).
 */
public final class HookCatalog {

    private HookCatalog() {
    }

    public static Set<String> implementationIds(WorkflowDefinition workflow) {
        Set<String> ids = new HashSet<>();
        for (HookDefinition hook : workflow.getHooks()) {
            addIfPresent(ids, hook.getPre());
            addIfPresent(ids, hook.getOnError());
            addIfPresent(ids, hook.getOnFinally());
        }
        return Set.copyOf(ids);
    }

    private static void addIfPresent(Set<String> ids, HookActionDefinition action) {
        if (action != null && action.getImplementationId() != null && !action.getImplementationId().isBlank()) {
            ids.add(action.getImplementationId());
        }
    }
}
