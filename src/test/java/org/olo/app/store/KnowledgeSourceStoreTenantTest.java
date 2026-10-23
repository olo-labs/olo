/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.store;

import org.junit.jupiter.api.Test;
import org.olo.app.tenant.TenantScopedName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSourceStoreTenantTest {

    private static KnowledgeSourceStore.KnowledgeSourceRecord record(String runId, String tenant, String name) {
        String stored = TenantScopedName.qualify(tenant, name);
        return new KnowledgeSourceStore.KnowledgeSourceRecord(runId, tenant, "files", stored, stored,
                List.of("a.pdf"), "documents-index", "q");
    }

    @Test
    void listShowsOnlyTheTenantsSourcesWithPlainNames() {
        KnowledgeSourceStore store = new KnowledgeSourceStore();
        store.putStarted(record("r1", "t1", "finance"));
        store.putStarted(record("r2", "t2", "finance"));

        assertThat(store.list("t1")).singleElement().satisfies(row -> {
            assertThat(row.get("runId")).isEqualTo("r1");
            assertThat(row.get("capabilitySource")).isEqualTo("finance");
            assertThat(row.get("sourceCollection")).isEqualTo("finance");
        });
        assertThat(store.list("t3")).isEmpty();
        assertThat(store.list(null)).isEmpty();
    }

    @Test
    void deletingOneTenantsSourceLeavesTheSameNameInAnotherTenant() {
        KnowledgeSourceStore store = new KnowledgeSourceStore();
        store.putStarted(record("r1", "t1", "finance"));
        store.putStarted(record("r2", "t2", "finance"));

        store.markDeleteStarted("d1", TenantScopedName.qualify("t1", "finance"));

        assertThat(store.list("t2").get(0).get("status")).isEqualTo("in_progress");
        assertThat(store.list("t2").get(0).get("message")).isEqualTo("Indexing in progress");
        assertThat(store.list("t1").get(0).get("message")).isEqualTo("Deleting knowledge source");
    }
}
