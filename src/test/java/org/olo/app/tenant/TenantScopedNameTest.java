/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantScopedNameTest {

    @Test
    void qualifiesAndUnqualifiesForTheSameTenant() {
        String stored = TenantScopedName.qualify("t1", "finance");

        assertThat(stored).isEqualTo("2-t1-finance");
        assertThat(TenantScopedName.unqualify("t1", stored)).contains("finance");
    }

    @Test
    void anotherTenantCanNotReadTheName() {
        String stored = TenantScopedName.qualify("t1", "finance");

        assertThat(TenantScopedName.unqualify("t2", stored)).isEmpty();
        assertThat(TenantScopedName.belongsTo("t2", stored)).isFalse();
    }

    @Test
    void encodingIsInjectiveEvenWithSeparatorsInTenantIds() {
        // Without the length prefix, ("a-b", "c") and ("a", "b-c") would both become "a-b-c".
        assertThat(TenantScopedName.qualify("a-b", "c")).isNotEqualTo(TenantScopedName.qualify("a", "b-c"));
        assertThat(TenantScopedName.unqualify("a", TenantScopedName.qualify("a-b", "c"))).isEmpty();
        assertThat(TenantScopedName.unqualify("a_", TenantScopedName.qualify("a", "_b"))).isEmpty();
    }

    @Test
    void unscopedNamesBelongToNoTenant() {
        assertThat(TenantScopedName.unqualify("t1", "finance")).isEmpty();
        assertThat(TenantScopedName.unqualify("t1", "2-t1-")).isEmpty();
    }

    @Test
    void rejectsInvalidTenantIdsAndBlankNames() {
        assertThatThrownBy(() -> TenantScopedName.qualify("../t", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantScopedName.qualify(null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantScopedName.qualify("t1", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
