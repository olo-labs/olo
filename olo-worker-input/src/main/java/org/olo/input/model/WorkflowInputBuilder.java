/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for {@link WorkflowInput}.
 */
public final class WorkflowInputBuilder {

    private String version;
    private final List<InputItem> inputs = new ArrayList<>();
    private Context context;
    private Routing routing;
    private Metadata metadata;

    public WorkflowInputBuilder version(String version) {
        this.version = version;
        return this;
    }

    public WorkflowInputBuilder addInput(InputItem input) {
        this.inputs.add(Objects.requireNonNull(input, "input"));
        return this;
    }

    public WorkflowInputBuilder inputs(List<InputItem> inputs) {
        this.inputs.clear();
        if (inputs != null) {
            this.inputs.addAll(inputs);
        }
        return this;
    }

    public WorkflowInputBuilder context(Context context) {
        this.context = context;
        return this;
    }

    public WorkflowInputBuilder routing(Routing routing) {
        this.routing = routing;
        return this;
    }

    public WorkflowInputBuilder metadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public WorkflowInput build() {
        return new WorkflowInput(
                version,
                List.copyOf(inputs),
                context,
                routing,
                metadata
        );
    }
}
