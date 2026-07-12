/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

import java.util.Objects;

/**
 * One step in a {@link DataPath} ({@code news}, optional {@code [0]} index).
 */
public final class PathSegment {

    private final String name;
    private final Integer index;

    public PathSegment(String name, Integer index) {
        this.name = Objects.requireNonNull(name, "segment name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("segment name must not be blank");
        }
        if (index != null && index < 0) {
            throw new IllegalArgumentException("segment index must be >= 0");
        }
        this.index = index;
    }

    public static PathSegment of(String name) {
        return new PathSegment(name, null);
    }

    public static PathSegment indexed(String name, int index) {
        return new PathSegment(name, index);
    }

    public String name() {
        return name;
    }

    public Integer index() {
        return index;
    }

    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public String toString() {
        return index == null ? name : name + "[" + index + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PathSegment that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, index);
    }
}
