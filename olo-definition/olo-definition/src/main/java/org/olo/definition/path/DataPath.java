/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

import java.util.List;
import java.util.Objects;

/**
 * Parsed reference path in the OLO path language.
 * <p>
 * Canonical forms: {@code state.analysis.score}, {@code state.news[0]}, {@code input.symbol},
 * {@code parameter.temperature}. Shorthand {@code symbol} is accepted as {@code state.symbol}.
 */
public final class DataPath {

    private final PathRoot root;
    private final List<PathSegment> segments;
    private final String literal;

    DataPath(PathRoot root, List<PathSegment> segments, String literal) {
        this.root = Objects.requireNonNull(root, "root is required");
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments are required"));
        this.literal = Objects.requireNonNull(literal, "literal is required");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("path must have at least one segment after root");
        }
    }

    public PathRoot root() {
        return root;
    }

    public List<PathSegment> segments() {
        return segments;
    }

    /** Original path string as written in the workflow file. */
    public String literal() {
        return literal;
    }

    /** Top-level declared field name for catalog validation ({@code analysis} in {@code state.analysis.score}). */
    public String topLevelName() {
        return segments.get(0).name();
    }

    /** Canonical serialized form using the path language. */
    public String canonical() {
        StringBuilder sb = new StringBuilder(root.prefix());
        for (PathSegment segment : segments) {
            sb.append('.').append(segment);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return canonical();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataPath that)) {
            return false;
        }
        return root == that.root && Objects.equals(segments, that.segments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, segments);
    }
}
