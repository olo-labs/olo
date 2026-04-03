/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.producer;

import org.olo.input.model.Routing;
import org.olo.input.model.WorkflowInput;

import java.util.Objects;

/**
 * Stores deserialized {@link WorkflowInput} to the session key
 * {@code <sessionDataPrefix><transactionId>:USERINPUT} (e.g. {@code olo:sessions:8huqpd42mizzgjOhJEH9C:USERINPUT}).
 * <p>
 * At workflow start: deserialize the payload to {@link WorkflowInput}, then call {@link #store(WorkflowInput, String, CacheWriter)}
 * so the input is available at the session key for the workflow run.
 */
public final class SessionUserInputStorage {

    private static final String USER_INPUT_SUFFIX = ":USERINPUT";

    private SessionUserInputStorage() {
    }

    /**
     * Builds the session key for user input: {@code sessionDataPrefix + transactionId + ":USERINPUT"}.
     *
     * @param sessionDataPrefix prefix from config (e.g. {@code OloConfig.getSessionDataPrefix()}, env {@code OLO_SESSION_DATA})
     * @param transactionId     from {@link Routing#getTransactionId()}
     * @return the full key (e.g. {@code olo:sessions:8huqpd42mizzgjOhJEH9C:USERINPUT})
     */
    public static String userInputKey(String sessionDataPrefix, String transactionId) {
        String prefix = sessionDataPrefix != null ? sessionDataPrefix : "";
        String txId = transactionId != null ? transactionId : "";
        return prefix + txId + USER_INPUT_SUFFIX;
    }

    /**
     * Deserializes the workflow input JSON and stores it at the session USERINPUT key.
     *
     * @param rawInput          the raw workflow input JSON string
     * @param sessionDataPrefix prefix from config (e.g. {@code OloConfig.getSessionDataPrefix()})
     * @param writer            cache writer (e.g. Redis)
     * @return the deserialized {@link WorkflowInput}
     */
    public static WorkflowInput deserializeAndStore(String rawInput, String sessionDataPrefix, CacheWriter writer) {
        WorkflowInput input = WorkflowInput.fromJson(rawInput);
        store(input, sessionDataPrefix, writer);
        return input;
    }

    /**
     * Stores the workflow input JSON at the session USERINPUT key.
     * Key: {@code sessionDataPrefix + input.getRouting().getTransactionId() + ":USERINPUT"}.
     * Value: {@code input.toJson()}.
     *
     * @param input             the deserialized workflow input
     * @param sessionDataPrefix prefix from config (e.g. {@code OloConfig.getSessionDataPrefix()})
     * @param writer            cache writer (e.g. Redis)
     */
    public static void store(WorkflowInput input, String sessionDataPrefix, CacheWriter writer) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(writer, "writer");
        String transactionId = input.getRouting() != null ? input.getRouting().getTransactionId() : null;
        String key = userInputKey(sessionDataPrefix, transactionId);
        writer.put(key, input.toJson());
    }
}
