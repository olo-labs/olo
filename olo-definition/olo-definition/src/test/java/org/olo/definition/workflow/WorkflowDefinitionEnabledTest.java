/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionEnabledTest {

  private final JsonWorkflowSerializer json = new JsonWorkflowSerializer();

  @Test
  void serializesAndDeserializesEnabledAtRoot() throws Exception {
    WorkflowDefinition workflow =
        WorkflowDefinition.builder().id("agent").enabled(true).label("Agent").build();

    String serialized = json.serialize(workflow);
    assertThat(serialized).contains("\"enabled\" : true");

    WorkflowDefinition roundTripped = json.deserialize(serialized);
    assertThat(roundTripped.isEnabled()).isTrue();
  }
}
