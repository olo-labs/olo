package org.olo.definition.parallel;

/**
 * How a {@link org.olo.definition.node.NodeType#PARALLEL} section synchronizes before continuing.
 */
public enum JoinStrategy {

    /** Continue when all branches complete. */
    ALL,
    /** Continue when any branch completes. */
    ANY,
    /** Continue when the first successful branch completes. */
    FIRST_SUCCESS,
    /** Continue when a quorum of branches complete (see {@link JoinDefinition#getQuorumCount()}). */
    QUORUM
}
