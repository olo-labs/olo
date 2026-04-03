/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Workflow metadata (e.g. RAG tag, timestamp).
 */
public final class Metadata {

    private final String ragTag;
    private final long timestamp;

    @JsonCreator
    public Metadata(
            @JsonProperty("ragTag") String ragTag,
            @JsonProperty("timestamp") long timestamp) {
        this.ragTag = ragTag;
        this.timestamp = timestamp;
    }

    public String getRagTag() {
        return ragTag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metadata metadata = (Metadata) o;
        return timestamp == metadata.timestamp && Objects.equals(ragTag, metadata.ragTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ragTag, timestamp);
    }
}
