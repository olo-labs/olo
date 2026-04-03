/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A single workflow input (name, type, storage, optional inline value).
 */
public final class InputItem {

    private final String name;
    private final String displayName;
    private final InputType type;
    private final Storage storage;
    private final String value;

    @JsonCreator
    public InputItem(
            @JsonProperty("name") String name,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("type") InputType type,
            @JsonProperty("storage") Storage storage,
            @JsonProperty("value") String value) {
        this.name = name;
        this.displayName = displayName;
        this.type = type;
        this.storage = storage;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public InputType getType() {
        return type;
    }

    public Storage getStorage() {
        return storage;
    }

    /**
     * Inline value when storage mode is LOCAL (e.g. for STRING). May be null for CACHE/FILE.
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputItem inputItem = (InputItem) o;
        return Objects.equals(name, inputItem.name)
                && Objects.equals(displayName, inputItem.displayName)
                && type == inputItem.type
                && Objects.equals(storage, inputItem.storage)
                && Objects.equals(value, inputItem.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, displayName, type, storage, value);
    }
}
