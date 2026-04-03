/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Pipeline and transaction routing for the workflow.
 */
public final class Routing {

    private final String pipeline;
    private final TransactionType transactionType;
    private final String transactionId;
    private final String configVersion;

    /** Constructor for JSON and when config version is not specified. */
    @JsonCreator
    public Routing(
            @JsonProperty("pipeline") String pipeline,
            @JsonProperty("transactionType") TransactionType transactionType,
            @JsonProperty("transactionId") String transactionId,
            @JsonProperty("configVersion") String configVersion) {
        this.pipeline = pipeline;
        this.transactionType = transactionType;
        this.transactionId = transactionId;
        this.configVersion = configVersion;
    }

    /** Convenience constructor without config version (no execution version pinning). */
    public Routing(String pipeline, TransactionType transactionType, String transactionId) {
        this(pipeline, transactionType, transactionId, null);
    }

    public String getPipeline() {
        return pipeline;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    /** Optional pipeline config version to pin this run to (e.g. "1.0"). When set, the activity validates config version. */
    public String getConfigVersion() {
        return configVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Routing routing = (Routing) o;
        return Objects.equals(pipeline, routing.pipeline)
                && transactionType == routing.transactionType
                && Objects.equals(transactionId, routing.transactionId)
                && Objects.equals(configVersion, routing.configVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipeline, transactionType, transactionId, configVersion);
    }
}
