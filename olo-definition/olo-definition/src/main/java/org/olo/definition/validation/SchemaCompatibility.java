/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.validation;

import org.olo.spi.port.PortSchemaCompatibility;

/**
 * Structural schema compatibility for port wiring (definition-time only, not full type inference).
 */
public final class SchemaCompatibility {

    private SchemaCompatibility() {}

    /**
     * Returns whether data produced on an output port may be consumed on an input port.
     *
     * @see PortSchemaCompatibility
     */
    public static boolean compatible(String outputSchema, String inputSchema) {
        return PortSchemaCompatibility.compatible(outputSchema, inputSchema);
    }
}
