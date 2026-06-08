package org.olo.input.consumer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Helpers for bridging {@link WorkflowInputValues} to runtime {@code input.*} paths.
 */
public final class WorkflowInputMaps {

    private WorkflowInputMaps() {
    }

    public static Map<String, Object> toInputMap(WorkflowInputValues values, Iterable<String> inputNames) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String name : inputNames) {
            Optional<String> value = values.getStringValue(name);
            value.ifPresent(v -> map.put(name, v));
        }
        return Map.copyOf(map);
    }
}
