/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service.impl.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Extracts and formats human-step prompt and decision text from worker event payloads. */
final class RunHumanStepTextUtils {

    private RunHumanStepTextUtils() {
    }

    /** Text shown when a HUMAN node is waiting: input/metadata/output keys message, prompt, title, text, question. */
    static String extractHumanStepPromptText(
            Map<String, Object> input, Map<String, Object> metadata, Map<String, Object> output) {
        String s = firstNonBlankString(input, "message", "prompt", "text", "question", "title");
        if (s != null) {
            return s;
        }
        s = firstNonBlankString(output, "prompt", "title", "message", "description", "text", "question");
        if (s != null) {
            return s;
        }
        return firstNonBlankString(metadata, "message", "prompt", "text", "question", "title");
    }

    /**
     * Worker sends options on input (preferred), or metadata / output:
     * list of strings, or list of maps with label or text.
     */
    static List<String> extractHumanStepOptionLines(
            Map<String, Object> input, Map<String, Object> metadata, Map<String, Object> output) {
        List<?> raw = firstOptionsList(input, metadata, output);
        return normalizeOptionsToLines(raw);
    }

    static String extractHumanDecisionText(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return null;
        }
        Object message = output.get("message");
        if (message instanceof String msg && !msg.trim().isEmpty()) {
            return msg.trim();
        }
        Object response = output.get("response");
        if (response instanceof String r && !r.trim().isEmpty()) {
            return r.trim();
        }
        return null;
    }

    /**
     * Conversation text: {@code User Input Step: …} on the first line, then one line per worker option.
     */
    static String formatHumanStepPromptForConversation(String promptText, List<String> optionLines) {
        String q = promptText != null ? promptText.trim() : "";
        StringBuilder sb = new StringBuilder();
        if (q.isEmpty()) {
            sb.append("User Input Step:");
        } else {
            sb.append("User Input Step: ").append(q);
        }
        if (optionLines != null) {
            for (String line : optionLines) {
                if (line != null && !line.isBlank()) {
                    sb.append("\n").append(line.trim());
                }
            }
        }
        return sb.toString();
    }

    private static String firstNonBlankString(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String str) {
                String t = str.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return null;
    }

    private static List<?> firstOptionsList(
            Map<String, Object> input, Map<String, Object> metadata, Map<String, Object> output) {
        Object o = getOptionsRaw(input);
        if (o == null) {
            o = getOptionsRaw(metadata);
        }
        if (o == null) {
            o = getOptionsRaw(output);
        }
        return o instanceof List<?> list ? list : null;
    }

    private static Object getOptionsRaw(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        return map.get("options");
    }

    private static List<String> normalizeOptionsToLines(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String s && !s.isBlank()) {
                lines.add(s.trim());
            } else if (item instanceof Map<?, ?> m) {
                Object label = m.get("label");
                if (label == null) {
                    label = m.get("text");
                }
                if (label != null) {
                    String t = label.toString().trim();
                    if (!t.isEmpty()) {
                        lines.add(t);
                    }
                }
            }
        }
        return List.copyOf(lines);
    }
}
