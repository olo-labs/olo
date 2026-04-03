/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Where and how an input value is stored (LOCAL, CACHE, S3, DB).
 * Null cache/file are omitted from JSON so storage can be {"mode":"LOCAL"} only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Storage {

    private final StorageMode mode;
    private final CacheStorage cache;
    private final FileStorage file;

    @JsonCreator
    public Storage(
            @JsonProperty("mode") StorageMode mode,
            @JsonProperty("cache") CacheStorage cache,
            @JsonProperty("file") FileStorage file) {
        this.mode = mode;
        this.cache = cache;
        this.file = file;
    }

    public StorageMode getMode() {
        return mode;
    }

    public CacheStorage getCache() {
        return cache;
    }

    public FileStorage getFile() {
        return file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Storage storage = (Storage) o;
        return mode == storage.mode
                && Objects.equals(cache, storage.cache)
                && Objects.equals(file, storage.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, cache, file);
    }
}
