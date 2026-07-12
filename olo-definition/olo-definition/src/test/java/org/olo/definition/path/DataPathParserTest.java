/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.definition.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataPathParserTest {

    @Test
    void parsesCanonicalExamples() {
        assertPath("state.analysis.score", PathRoot.STATE, "analysis", "score");
        assertPath("state.news[0]", PathRoot.STATE, "news");
        assertThat(DataPathParser.parse("state.news[0]").path().orElseThrow().segments().get(0).index())
                .isEqualTo(0);
        assertPath("input.symbol", PathRoot.INPUT, "symbol");
        assertPath("parameter.temperature", PathRoot.PARAMETER, "temperature");
    }

    @Test
    void bareNameIsStateShorthand() {
        DataPath path = DataPathParser.parse("symbol").path().orElseThrow();
        assertThat(path.root()).isEqualTo(PathRoot.STATE);
        assertThat(path.topLevelName()).isEqualTo("symbol");
        assertThat(path.canonical()).isEqualTo("state.symbol");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "state", "state.", "input.", "state..symbol", "state.news[-1]", "foo.bar"})
    void rejectsInvalidPaths(String invalid) {
        assertThat(DataPathParser.parse(invalid).isSuccess()).isFalse();
    }

    @Test
    void rejectsTypoInStateField() {
        assertThat(DataPathParser.parse("state.analysys").isSuccess()).isTrue();
        assertThat(DataPathParser.parse("state.analysys").path().orElseThrow().topLevelName()).isEqualTo("analysys");
    }

    private static void assertPath(String literal, PathRoot root, String... segmentNames) {
        DataPath path = DataPathParser.parse(literal).path().orElseThrow();
        assertThat(path.root()).isEqualTo(root);
        assertThat(path.literal()).isEqualTo(literal);
        assertThat(path.segments()).hasSize(segmentNames.length);
        for (int i = 0; i < segmentNames.length; i++) {
            assertThat(path.segments().get(i).name()).isEqualTo(segmentNames[i]);
        }
        assertThat(path.topLevelName()).isEqualTo(segmentNames[0]);
    }
}
