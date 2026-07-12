/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.validation;

import org.olo.input.consumer.WorkflowInputValues;
import org.olo.definition.capability.CapabilityDefinition;
import org.olo.definition.input.WorkflowInputDefinition;
import org.olo.definition.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowInvocationValidatorTest {

    private static CapabilityDefinition minimalCapability() {
        return CapabilityDefinition.builder()
                .name("Test")
                .description("Test workflow")
                .addRequiredInput("in")
                .addRequiredOutput("out")
                .build();
    }

    @Test
    void acceptsRequiredInputsPresent() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("stock-analysis")
                .capability(minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").required(true).build())
                .build();

        WorkflowInputValues values = new StubWorkflowInputValues().put("symbol", "INFY");

        assertThat(WorkflowInvocationValidator.validate(workflow, values).valid()).isTrue();
    }

    @Test
    void rejectsMissingRequiredInput() {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .id("stock-analysis")
                .capability(minimalCapability())
                .putInput("symbol", WorkflowInputDefinition.builder().schema("String").required(true).build())
                .build();

        WorkflowInputValues values = new StubWorkflowInputValues();

        WorkflowInvocationValidationResult result = WorkflowInvocationValidator.validate(workflow, values);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("required workflow input missing: symbol"));
    }
}
