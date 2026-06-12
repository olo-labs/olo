package org.olo.definition.execution;

/**
 * Catalog-aligned orchestration model for how a step is scheduled.
 * <p>
 * Distinct from {@link ExecutionKind} (engine scheduling primitive). {@code AGENT} nodes use
 * {@link #CHILD_WORKFLOW} with {@link ExecutionKind#SUBWORKFLOW}.
 */
public enum ExecutionModel {

    INLINE,
    ACTIVITY,
    CHILD_WORKFLOW,
    EXTERNAL;

    /** Expected {@link ExecutionKind} when this model is set on a node. */
    public ExecutionKind expectedExecutionKind() {
        return switch (this) {
            case INLINE, ACTIVITY -> ExecutionKind.ACTIVITY;
            case CHILD_WORKFLOW -> ExecutionKind.SUBWORKFLOW;
            case EXTERNAL -> ExecutionKind.EVENT;
        };
    }
}
