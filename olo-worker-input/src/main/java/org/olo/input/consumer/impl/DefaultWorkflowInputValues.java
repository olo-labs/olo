/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.consumer.impl;

import org.olo.input.consumer.CacheReader;
import org.olo.input.consumer.FileReader;
import org.olo.input.consumer.WorkflowInputValues;
import org.olo.input.model.FileStorage;
import org.olo.input.model.InputItem;
import org.olo.input.model.Storage;
import org.olo.input.model.WorkflowInput;

import java.util.Optional;

/**
 * Resolves workflow input values from {@link WorkflowInput} using {@link CacheReader} and {@link FileReader}.
 * Implements the consumer contract {@link WorkflowInputValues}: read-only, storage-agnostic access.
 */
public final class DefaultWorkflowInputValues implements WorkflowInputValues {

    private final WorkflowInput input;
    private final CacheReader cacheReader;
    private final FileReader fileReader;

    public DefaultWorkflowInputValues(WorkflowInput input, CacheReader cacheReader, FileReader fileReader) {
        this.input = input;
        this.cacheReader = cacheReader;
        this.fileReader = fileReader;
    }

    @Override
    public Optional<String> getStringValue(String inputName) {
        InputItem item = findInput(inputName);
        if (item == null) return Optional.empty();
        return resolveString(item);
    }

    @Override
    public Optional<Double> getNumberValue(String inputName) {
        Optional<String> raw = getStringValue(inputName);
        if (raw.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(raw.get().trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Boolean> getBooleanValue(String inputName) {
        Optional<String> raw = getStringValue(inputName);
        if (raw.isEmpty()) return Optional.empty();
        String s = raw.get().trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s)) return Optional.of(true);
        if ("false".equals(s) || "0".equals(s)) return Optional.of(false);
        return Optional.empty();
    }

    @Override
    public Optional<String> getFileContentAsString(String inputName) {
        InputItem item = findInput(inputName);
        if (item == null) return Optional.empty();
        Storage storage = item.getStorage();
        if (storage == null || storage.getFile() == null) return Optional.empty();
        FileStorage file = storage.getFile();
        return fileReader.readAsString(file.getRelativeFolder(), file.getFileName());
    }

    @Override
    public Optional<byte[]> getFileContent(String inputName) {
        InputItem item = findInput(inputName);
        if (item == null) return Optional.empty();
        Storage storage = item.getStorage();
        if (storage == null || storage.getFile() == null) return Optional.empty();
        FileStorage file = storage.getFile();
        return fileReader.readAsBytes(file.getRelativeFolder(), file.getFileName());
    }

    private InputItem findInput(String name) {
        if (name == null || input == null || input.getInputs() == null) return null;
        for (InputItem item : input.getInputs()) {
            if (name.equals(item.getName())) return item;
        }
        return null;
    }

    private Optional<String> resolveString(InputItem item) {
        Storage storage = item.getStorage();
        if (storage == null) return Optional.ofNullable(item.getValue());

        switch (storage.getMode()) {
            case LOCAL:
                if (storage.getFile() != null) {
                    FileStorage file = storage.getFile();
                    return fileReader.readAsString(file.getRelativeFolder(), file.getFileName());
                }
                return Optional.ofNullable(item.getValue());
            case CACHE:
                if (storage.getCache() != null) {
                    return cacheReader.get(storage.getCache().getKey());
                }
                return Optional.empty();
            case S3:
            case DB:
                return Optional.empty();
            default:
                return Optional.ofNullable(item.getValue());
        }
    }
}
