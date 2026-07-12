/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaCompatibilityTest {

    @Test
    void acceptsExactSchemaMatch() {
        assertThat(SchemaCompatibility.compatible("Stock[]", "Stock[]")).isTrue();
    }

    @Test
    void acceptsTargetAny() {
        assertThat(SchemaCompatibility.compatible("String", "any")).isTrue();
        assertThat(SchemaCompatibility.compatible("String", "*")).isTrue();
    }

    @Test
    void rejectsMismatchedSchemas() {
        assertThat(SchemaCompatibility.compatible("String", "Stock[]")).isFalse();
    }

    @Test
    void rejectsStringToNumberWithoutCoercion() {
        assertThat(SchemaCompatibility.compatible("string", "number")).isFalse();
        assertThat(SchemaCompatibility.compatible("string", "string")).isTrue();
    }
}
