/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.producer;

import org.olo.input.config.MaxLocalMessageSize;
import org.olo.input.model.CacheProvider;
import org.olo.input.model.CacheStorage;
import org.olo.input.model.Context;
import org.olo.input.model.FileStorage;
import org.olo.input.model.InputItem;
import org.olo.input.model.InputType;
import org.olo.input.model.Metadata;
import org.olo.input.model.Routing;
import org.olo.input.model.Storage;
import org.olo.input.model.StorageMode;
import org.olo.input.model.WorkflowInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Producer-side builder for {@link WorkflowInput}. When adding string inputs, values larger than
 * {@code maxLocalMessageSize} are stored via {@link CacheWriter} and the cache key is put in the payload
 * (CACHE storage); smaller values are inlined (LOCAL). The producer has full read/write access; the consumer
 * only sees the read-only {@link org.olo.input.consumer.WorkflowInputValues} contract.
 */
public final class WorkflowInputProducer {

    private final int maxLocalMessageSize;
    private final CacheWriter cacheWriter;
    private final String transactionId;
    private final String version;
    private Context context;
    private Routing routing;
    private Metadata metadata;
    private final List<InputItem> inputs = new ArrayList<>();

    private WorkflowInputProducer(int maxLocalMessageSize, CacheWriter cacheWriter, String transactionId, String version) {
        this.maxLocalMessageSize = maxLocalMessageSize;
        this.cacheWriter = cacheWriter;
        this.transactionId = transactionId;
        this.version = version;
    }

    /**
     * Creates a producer with the given constraints. Use {@link MaxLocalMessageSize#fromEnvironment()} for max size from env.
     */
    public static WorkflowInputProducer create(int maxLocalMessageSize, CacheWriter cacheWriter, String transactionId, String version) {
        Objects.requireNonNull(cacheWriter, "cacheWriter");
        Objects.requireNonNull(transactionId, "transactionId");
        return new WorkflowInputProducer(maxLocalMessageSize, cacheWriter, transactionId, version != null ? version : "1.0");
    }

    public WorkflowInputProducer context(Context context) {
        this.context = context;
        return this;
    }

    public WorkflowInputProducer routing(Routing routing) {
        this.routing = routing;
        return this;
    }

    public WorkflowInputProducer metadata(Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Adds a string input. If {@code value.length() > maxLocalMessageSize}, stores the value in cache and adds a CACHE storage entry with the shared key; otherwise adds LOCAL with inline value.
     */
    public WorkflowInputProducer addStringInput(String name, String displayName, String value) {
        String display = displayName != null ? displayName : name;
        if (value != null && value.length() > maxLocalMessageSize) {
            String tenantId = context != null ? context.getTenantId() : null;
            String key = InputStorageKeys.cacheKey(tenantId, transactionId, name);
            cacheWriter.put(key, value);
            Storage storage = new Storage(StorageMode.CACHE, new CacheStorage(CacheProvider.REDIS, key), null);
            inputs.add(new InputItem(name, display, InputType.STRING, storage, null));
        } else {
            Storage storage = new Storage(StorageMode.LOCAL, null, null);
            inputs.add(new InputItem(name, display, InputType.STRING, storage, value));
        }
        return this;
    }

    /**
     * Adds a file input (LOCAL file reference).
     */
    public WorkflowInputProducer addFileInput(String name, String displayName, String relativeFolder, String fileName) {
        String display = displayName != null ? displayName : name;
        FileStorage file = new FileStorage(relativeFolder, fileName);
        Storage storage = new Storage(StorageMode.LOCAL, null, file);
        inputs.add(new InputItem(name, display, InputType.FILE, storage, null));
        return this;
    }

    /**
     * Adds a raw input item (full control for producer). Use when you need a specific storage type (e.g. CACHE with a pre-existing key).
     */
    public WorkflowInputProducer addInput(InputItem item) {
        inputs.add(Objects.requireNonNull(item, "item"));
        return this;
    }

    /**
     * Builds the {@link WorkflowInput}. Can be serialized with {@link WorkflowInput#toJson()} and sent as the workflow creation payload.
     */
    public WorkflowInput build() {
        return new WorkflowInput(version, List.copyOf(inputs), context, routing, metadata);
    }
}
