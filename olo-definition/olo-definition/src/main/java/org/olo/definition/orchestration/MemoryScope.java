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
