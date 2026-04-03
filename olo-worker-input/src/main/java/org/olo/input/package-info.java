/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

/**
 * OLO worker input module: workflow input model, consumer contract, and producer builder.
 *
 * <ul>
 *   <li>{@link org.olo.input.model} – payload DTOs and enums (WorkflowInput, InputItem, Storage, Context, Routing, Metadata, etc.)</li>
 *   <li>{@link org.olo.input.consumer} – read-only contract ({@link org.olo.input.consumer.WorkflowInputValues}) and resolution ({@link org.olo.input.consumer.impl.DefaultWorkflowInputValues}, CacheReader, FileReader)</li>
 *   <li>{@link org.olo.input.producer} – builder and cache write contract (WorkflowInputProducer, CacheWriter, InputStorageKeys)</li>
 *   <li>{@link org.olo.input.config} – configuration (MaxLocalMessageSize)</li>
 * </ul>
 */
package org.olo.input;
