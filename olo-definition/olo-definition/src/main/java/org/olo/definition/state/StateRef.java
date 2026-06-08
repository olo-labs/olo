package org.olo.definition.state;

import org.olo.definition.path.DataPath;
import org.olo.definition.path.DataPathParseResult;
import org.olo.definition.path.DataPathParser;
import org.olo.definition.path.PathRoot;

/**
 * Legacy helpers for state-only paths. Prefer {@link DataPathParser} and {@link DataPath}.
 *
 * @deprecated use {@code org.olo.definition.path}
 */
@Deprecated
public final class StateRef {

    private StateRef() {
    }

    /**
     * @deprecated use {@link DataPath#topLevelName()} on a parsed {@link DataPath}
     */
    @Deprecated
    public static String fieldName(String path) {
        DataPathParseResult result = DataPathParser.parse(path);
        if (result.isSuccess() && result.path().orElseThrow().root() == PathRoot.STATE) {
            return result.path().orElseThrow().topLevelName();
        }
        if (path == null || path.isBlank()) {
            return path;
        }
        String trimmed = path.trim();
        if (trimmed.startsWith("state.")) {
            return trimmed.substring("state.".length()).split("\\.", 2)[0].replaceAll("\\[\\d+\\]$", "");
        }
        return trimmed.split("\\.", 2)[0].replaceAll("\\[\\d+\\]$", "");
    }

    /**
     * @deprecated use {@link DataPathParser#parse(String)}
     */
    @Deprecated
    public static boolean hasResolvableFieldName(String path) {
        return DataPathParser.parse(path).isSuccess();
    }
}
