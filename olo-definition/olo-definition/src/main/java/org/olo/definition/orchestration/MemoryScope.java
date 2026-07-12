/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.orchestration;

/**
 * Whether delegated agents share workflow state or keep isolated memory.
 */
public enum MemoryScope {
    /** Delegated agents read and write the parent workflow state. */
    SHARED,
    /** Each delegated agent keeps private memory; no shared state with siblings or parent. */
    PRIVATE
}
