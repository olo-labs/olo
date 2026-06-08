package org.olo.definition.validation;

import java.util.Locale;

/**
 * Structural schema compatibility for port wiring (definition-time only, not full type inference).
 */
public final class SchemaCompatibility {

    private SchemaCompatibility() {
    }

    /**
     * Returns whether data produced on an output port may be consumed on an input port.
     * Schemas match exactly, or the target accepts {@code any} / {@code *}.
     */
    public static boolean compatible(String outputSchema, String inputSchema) {
        if (isBlank(outputSchema) || isBlank(inputSchema)) {
            return false;
        }
        String output = normalize(outputSchema);
        String input = normalize(inputSchema);
        if (acceptsAny(input)) {
            return true;
        }
        return output.equals(input);
    }

    private static boolean acceptsAny(String schema) {
        return "any".equals(schema) || "*".equals(schema);
    }

    private static String normalize(String schema) {
        return schema.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
