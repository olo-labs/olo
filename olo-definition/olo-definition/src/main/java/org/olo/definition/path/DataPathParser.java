/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the OLO data path language used in node {@code reads}/{@code writes}, mappings, and overrides.
 */
public final class DataPathParser {

    private static final Pattern BARE_SEGMENT = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern QUALIFIED =
            Pattern.compile("^(?<root>state|input|parameter)\\.(?<rest>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEGMENT =
            Pattern.compile("^(?<name>[a-zA-Z_][a-zA-Z0-9_]*)(?:\\[(?<index>\\d+)\\])?$");

    private DataPathParser() {
    }

    public static DataPathParseResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DataPathParseResult.failure("path must not be blank");
        }
        String literal = raw.trim();

        Matcher qualified = QUALIFIED.matcher(literal);
        if (qualified.matches()) {
            PathRoot root = PathRoot.fromPrefix(qualified.group("root"));
            return parseSegments(root, qualified.group("rest"), literal);
        }

        if (BARE_SEGMENT.matcher(literal).matches()) {
            if (PathRoot.fromPrefix(literal) != null) {
                return DataPathParseResult.failure(
                        "path must include a field after root '" + literal + "' (e.g. " + literal + ".field)");
            }
            return DataPathParseResult.success(
                    new DataPath(PathRoot.STATE, List.of(PathSegment.of(literal)), literal));
        }

        return DataPathParseResult.failure(
                "path must be qualified (state.|input.|parameter.) or a bare state field name: " + literal);
    }

    private static DataPathParseResult parseSegments(PathRoot root, String rest, String literal) {
        if (rest == null || rest.isBlank()) {
            return DataPathParseResult.failure("path must include at least one segment after " + root.prefix());
        }
        List<PathSegment> segments = new ArrayList<>();
        for (String part : rest.split("\\.")) {
            if (part.isBlank()) {
                return DataPathParseResult.failure("path contains empty segment in: " + literal);
            }
            Matcher segmentMatcher = SEGMENT.matcher(part);
            if (!segmentMatcher.matches()) {
                return DataPathParseResult.failure("invalid path segment '" + part + "' in: " + literal);
            }
            String name = segmentMatcher.group("name");
            String indexGroup = segmentMatcher.group("index");
            if (indexGroup == null) {
                segments.add(PathSegment.of(name));
            } else {
                segments.add(PathSegment.indexed(name, Integer.parseInt(indexGroup)));
            }
        }
        return DataPathParseResult.success(new DataPath(root, segments, literal));
    }
}
