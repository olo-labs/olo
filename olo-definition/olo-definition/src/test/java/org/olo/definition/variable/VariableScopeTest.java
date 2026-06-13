package org.olo.definition.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VariableScopeTest {

    @Test
    void parsesKnownScopes() {
        assertEquals(VariableScope.EXTERNAL, VariableScope.fromValue("EXTERNAL"));
        assertEquals(VariableScope.READONLY_EXTERNAL, VariableScope.fromValue("readonly_external"));
    }

    @Test
    void rejectsUnknownScope() {
        assertThrows(IllegalArgumentException.class, () -> VariableScope.fromValue("PRIVATE"));
    }
}
