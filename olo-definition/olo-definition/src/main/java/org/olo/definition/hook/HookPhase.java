/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.hook;

/**
 * Lifecycle phase for a workflow hook binding.
 */
public enum HookPhase {
    /** Runs before matched nodes execute. */
    PRE,
    /** Runs when a matched node fails. */
    ON_ERROR,
    /** Runs after matched nodes complete (success or failure). */
    FINALLY
}
