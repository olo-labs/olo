package org.olo.definition.planner;

import java.util.Locale;
import java.util.Optional;

/**
 * How the router picks a delegate from {@code availableAgents} (e.g. best reviewer vs fixed mapping).
 */
public enum AgentSelectionStrategy {
    /** Runtime router chooses the best agent by capability, cost, or policy. */
    DYNAMIC("dynamic"),
    /** Fixed mapping from task type to agent id. */
    STATIC("static");

    private final String wireName;

    AgentSelectionStrategy(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<AgentSelectionStrategy> fromWire(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AgentSelectionStrategy strategy : values()) {
            if (strategy.wireName.equals(normalized)) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
