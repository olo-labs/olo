/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.workflow;

import org.junit.jupiter.api.Test;
import org.olo.app.store.ChatRunStore;
import org.olo.app.workflow.impl.SdkWorkflowRunner;
import org.olo.input.codec.TenantWorkflowIds;
import org.olo.temporal.sdk.TemporalClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** M5.2: run workflow ids carry the run's tenant, so the payload codec seals each run with its tenant key. */
class TenantWorkflowIdTest {

    @Test
    void signalAndCancelAddressTheTenantWorkflowOfTheRun() {
        TemporalClient temporal = mock(TemporalClient.class);
        ChatRunStore runs = new ChatRunStore();
        runs.put(new ChatRunStore.RunRecord("run-1", "s1", "m1", "t1", "c1", null, null, null));
        SdkWorkflowRunner runner = new SdkWorkflowRunner(temporal, "olo-chat", Runnable::run, runs);

        runner.signalHumanInput("run-1", true, "ok");
        runner.cancelChatRun("run-1");

        verify(temporal).signalHumanInput("run-t1~run-1", true, "ok");
        verify(temporal).cancelChatWorkflow("run-t1~run-1");
        assertThat(TenantWorkflowIds.tenantOf("run-t1~run-1")).isEqualTo("t1");
        assertThatThrownBy(() -> runner.cancelChatRun("unknown")).isInstanceOf(IllegalArgumentException.class);
    }
}
