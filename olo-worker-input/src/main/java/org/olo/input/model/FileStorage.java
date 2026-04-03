/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * File storage details when storage mode is LOCAL and input type is FILE.
 */
public final class FileStorage {

    private final String relativeFolder;
    private final String fileName;

    @JsonCreator
    public FileStorage(
            @JsonProperty("relativeFolder") String relativeFolder,
            @JsonProperty("fileName") String fileName) {
        this.relativeFolder = relativeFolder;
        this.fileName = fileName;
    }

    public String getRelativeFolder() {
        return relativeFolder;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileStorage that = (FileStorage) o;
        return Objects.equals(relativeFolder, that.relativeFolder) && Objects.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativeFolder, fileName);
    }
}
