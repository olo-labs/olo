package org.olo.definition.node;



/**

 * Business / logical node types for AI orchestration graphs.

 * <p>

 * {@link NodeDefinition#getType()} is stored as a string so custom types remain valid.

 * Pair with {@link org.olo.definition.execution.ExecutionKind} for runtime scheduling semantics.

 * Child workflows are referenced via {@link org.olo.definition.workflow.WorkflowReferenceDefinition}

 * on {@code workflow}, not inline subgraph types.

 */

public enum NodeType {



    /** Workflow entry: accepts external or upstream payload. */

    INPUT,

    /** Workflow exit: returns result to caller or downstream system. */

    OUTPUT,



    /** Single LLM or embedding call (use {@code subtype} e.g. {@code CHAT}, {@code EMBEDDING}). */

    MODEL,

    /** Vector index lookup / similarity search. */

    VECTOR_SEARCH,

    /** Deterministic or registered callable (API, function, MCP tool). */

    TOOL,

    /** Legacy alias for retrieval-focused nodes; prefer {@link #VECTOR_SEARCH} for new graphs. */

    RETRIEVER,

    /** Conversation or session memory read/write. */

    MEMORY,



    /**

     * Autonomous logical actor. <strong>Requires</strong> {@link NodeDefinition#getWorkflow()}.

     * Executed as a child workflow ({@code executionKind} {@code SUBWORKFLOW}).

     */

    AGENT,

    /** Decomposes goals into steps or sub-tasks (activity or child workflow). */

    PLANNER,

    /** Critiques or revises prior output (activity or child workflow). */

    REFLECTION,

    /** Scores or judges output quality (activity or child workflow). */

    EVALUATOR,



    /**

     * Invokes a versioned workflow artifact by reference. Requires {@code workflow}.

     */

    WORKFLOW_REF,

    /** Branching on expression or match rules ({@code routers}, named output ports). */

    CONDITION,

    /** Dynamic routing to targets by rules (distinct from {@link #CONDITION} expression nodes). */

    ROUTER,

    /**

     * Fan-out parallel section. Requires {@link NodeDefinition#getJoin()} ({@link org.olo.definition.parallel.JoinDefinition}).

     */

    PARALLEL,



    /**

     * Human-in-the-loop gate. Use {@code approval} and {@code executionKind} {@code HUMAN_WAIT}.

     */

    HUMAN;



    public String value() {

        return name();

    }

}


