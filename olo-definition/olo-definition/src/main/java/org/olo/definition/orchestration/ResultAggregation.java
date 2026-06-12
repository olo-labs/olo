package org.olo.definition.orchestration;

import java.util.Locale;
import java.util.Optional;

/**
 * How a delegating agent combines outputs from child agents (e.g. architect design + reviewer critique).
 * <p>
 * Distinct from workflow-level {@code metadata.returnVariable}, which names where the final answer is stored.
 */
public enum ResultAggregation {
    /** Synthesize delegated outputs into one combined response. */
    MERGE,
    /** Select the single best delegated response by policy or evaluator. */
    BEST_RESPONSE;

    public static Optional<ResultAggregation> fromWire(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ResultAggregation strategy : values()) {
            if (strategy.name().equals(normalized)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
