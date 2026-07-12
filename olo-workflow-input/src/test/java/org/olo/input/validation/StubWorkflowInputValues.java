/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.input.validation;

import org.olo.input.consumer.WorkflowInputValues;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class StubWorkflowInputValues implements WorkflowInputValues {

    private final Map<String, String> strings = new HashMap<>();

    StubWorkflowInputValues put(String name, String value) {
        strings.put(name, value);
        return this;
    }

    @Override
    public Optional<String> getStringValue(String inputName) {
        return Optional.ofNullable(strings.get(inputName));
    }

    @Override
    public Optional<Double> getNumberValue(String inputName) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBooleanValue(String inputName) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getFileContentAsString(String inputName) {
        return Optional.empty();
    }

    @Override
    public Optional<byte[]> getFileContent(String inputName) {
        return Optional.empty();
    }
}
