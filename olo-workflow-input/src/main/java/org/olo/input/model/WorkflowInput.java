package org.olo.input.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * Root workflow input: version, inputs, context, routing, metadata, execution.
 * Supports JSON serialization/deserialization and a fluent builder.
 * Can be deserialized from a JSON object or from a string containing JSON (e.g. Temporal payload).
 * When the payload is a string (Temporal sends workflow arg as one string), Jackson uses the
 * static String-argument creator so any worker's ObjectMapper can deserialize without a custom deserializer.
 */
public final class WorkflowInput {

    private static final ObjectMapper MAPPER = WorkflowInputMapper.jsonMapper();

    private final String version;
    private final List<InputItem> inputs;
    private final Context context;
    private final Routing routing;
    private final Metadata metadata;
    private final Execution execution;

    /** Used when the payload is a JSON object. */
    @JsonCreator
    public WorkflowInput(
            @JsonProperty("version") String version,
            @JsonProperty("inputs") List<InputItem> inputs,
            @JsonProperty("context") Context context,
            @JsonProperty("routing") Routing routing,
            @JsonProperty("metadata") Metadata metadata,
            @JsonProperty("execution") Execution execution) {
        this.version = version;
        this.inputs = inputs != null ? List.copyOf(inputs) : List.of();
        this.context = context;
        this.routing = routing;
        this.metadata = metadata;
        this.execution = execution;
    }

    /**
     * Delegating creator: used when the payload is a JSON string (e.g. Temporal workflow argument).
     * Enables any worker's ObjectMapper to deserialize without registering a custom deserializer.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WorkflowInput fromJsonString(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json.trim(), WorkflowInput.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getVersion() {
        return version;
    }

    public List<InputItem> getInputs() {
        return inputs;
    }

    public Context getContext() {
        return context;
    }

    public Routing getRouting() {
        return routing;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public Execution getExecution() {
        return execution;
    }

    /**
     * Deserializes from JSON string. Throws {@link UncheckedIOException} on failure.
     */
    public static WorkflowInput fromJson(String json) {
        try {
            return MAPPER.readValue(json, WorkflowInput.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Serializes this instance to a JSON string. Throws {@link UncheckedIOException} on failure.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    /**
     * Serializes this instance to a pretty-printed JSON string.
     */
    public String toJsonPretty() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }

    public static WorkflowInputBuilder builder() {
        return new WorkflowInputBuilder();
    }

    /**
     * Returns an independent immutable copy of this payload.
     * Use {@link #toBuilder()} when you intend to modify the copy before building.
     */
    public WorkflowInput copy() {
        return WorkflowInputBuilder.from(this).build();
    }

    /**
     * Returns a mutable builder seeded from this payload (copy-on-write).
     */
    public WorkflowInputBuilder toBuilder() {
        return WorkflowInputBuilder.from(this);
    }

    /**
     * Returns an independent immutable copy of {@code source}.
     */
    public static WorkflowInput copyOf(WorkflowInput source) {
        Objects.requireNonNull(source, "source workflow input is required");
        return source.copy();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowInput that = (WorkflowInput) o;
        return Objects.equals(version, that.version)
                && Objects.equals(inputs, that.inputs)
                && Objects.equals(context, that.context)
                && Objects.equals(routing, that.routing)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(execution, that.execution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, inputs, context, routing, metadata, execution);
    }
}
