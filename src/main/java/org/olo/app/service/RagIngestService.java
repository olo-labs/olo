/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.domain.EventType;
import org.olo.app.service.impl.run.RunEventHandler;
import org.olo.app.service.impl.run.RunWorkflowStarter;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.KnowledgeSourceStore;
import org.olo.app.workflow.impl.WorkflowInputSerializer;
import org.olo.input.model.WorkflowInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RagIngestService {

    private final RunWorkflowStarter workflowStarter;
    private final RunEventHandler eventHandler;
    private final ChatRunStore runStore;
    private final KnowledgeSourceStore knowledgeSourceStore;
    private final ResourceUploadService resourceUploadService;
    private final String defaultTaskQueue;
    private final String defaultPipeline;
    private final String defaultDeletePipeline;
    private final String ragIngestQueue;
    private final String ragDeleteQueue;
    private final String callbackBaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagIngestService(RunWorkflowStarter workflowStarter,
                            RunEventHandler eventHandler,
                            ChatRunStore runStore,
                            KnowledgeSourceStore knowledgeSourceStore,
                            ResourceUploadService resourceUploadService,
                            @Qualifier("oloTaskQueue") String defaultTaskQueue,
                            @Value("${olo.rag.ingest.pipeline:documents-index}") String defaultPipeline,
                            @Value("${olo.rag.delete.pipeline:documents-delete}") String defaultDeletePipeline,
                            @Value("${olo.rag.ingest.queue:}") String ragIngestQueue,
                            @Value("${olo.rag.delete.queue:}") String ragDeleteQueue,
                            @Qualifier("oloCallbackBaseUrl") String callbackBaseUrl) {
        this.workflowStarter = workflowStarter;
        this.eventHandler = eventHandler;
        this.runStore = runStore;
        this.knowledgeSourceStore = knowledgeSourceStore;
        this.resourceUploadService = resourceUploadService;
        this.defaultTaskQueue = defaultTaskQueue;
        this.defaultPipeline = defaultPipeline;
        this.defaultDeletePipeline = defaultDeletePipeline;
        this.ragIngestQueue = ragIngestQueue == null ? "" : ragIngestQueue.trim();
        this.ragDeleteQueue = ragDeleteQueue == null ? "" : ragDeleteQueue.trim();
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public Map<String, Object> startIngest(
            String tenantId,
            String sourceType,
            String capabilitySource,
            String knowledgeName,
            List<String> fileNames,
            String taskQueue,
            String pipelineId) throws JsonProcessingException {

        String source = capabilitySource == null ? "" : capabilitySource.trim();
        if (source.isEmpty()) {
            return Map.of("success", false, "message", "capabilitySource is required");
        }

        List<String> resolvedFiles = fileNames == null ? List.of() : fileNames.stream()
                .filter(f -> f != null && !f.isBlank())
                .map(String::trim)
                .toList();

        String runId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        String effectiveSourceType = sourceType == null || sourceType.isBlank() ? "files" : sourceType.trim();
        String effectiveKnowledgeName = knowledgeName == null || knowledgeName.isBlank() ? source : knowledgeName.trim();
        String effectiveQueue = taskQueue == null || taskQueue.isBlank()
                ? (ragIngestQueue.isBlank() ? defaultTaskQueue : ragIngestQueue)
                : taskQueue.trim();
        String effectivePipeline = pipelineId == null || pipelineId.isBlank() ? defaultPipeline : pipelineId.trim();

        runStore.put(new ChatRunStore.RunRecord(
                runId,
                "rag-ingest",
                runId,
                effectiveTenant,
                correlationId,
                null,
                null,
                null));

        knowledgeSourceStore.putStarted(new KnowledgeSourceStore.KnowledgeSourceRecord(
                runId,
                effectiveTenant,
                effectiveSourceType,
                source,
                effectiveKnowledgeName,
                resolvedFiles,
                effectivePipeline,
                effectiveQueue));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceType", effectiveSourceType);
        payload.put("capabilitySource", source);
        payload.put("knowledgeName", effectiveKnowledgeName);
        payload.put("ragTag", source);
        if (!resolvedFiles.isEmpty()) {
            payload.put("fileNames", resolvedFiles);
            payload.put("fileNamesCsv", String.join(",", resolvedFiles));
        }
        String payloadJson = objectMapper.writeValueAsString(payload);

        eventHandler.appendEvent(
                runId,
                "root",
                null,
                "SYSTEM",
                "STARTED",
                Map.of("type", "rag-ingest",
                        "sourceType", effectiveSourceType,
                        "capabilitySource", source,
                        "knowledgeName", effectiveKnowledgeName,
                        "fileCount", resolvedFiles.size()),
                null,
                Map.of("tenantId", effectiveTenant,
                        "sourceType", effectiveSourceType,
                        "capabilitySource", source,
                        "knowledgeName", effectiveKnowledgeName),
                null,
                null,
                EventType.NODE_STARTED,
                correlationId);

        WorkflowInput workflowInput = WorkflowInputSerializer.buildRagIngest(
                effectiveTenant,
                source,
                payloadJson,
                effectivePipeline,
                runId,
                runId,
                callbackBaseUrl,
                correlationId);

        workflowStarter.startWorkflow(runId, workflowInput, effectiveQueue);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("runId", runId);
        body.put("capabilitySource", source);
        body.put("sourceType", effectiveSourceType);
        body.put("knowledgeName", effectiveKnowledgeName);
        body.put("pipeline", effectivePipeline);
        body.put("taskQueue", effectiveQueue);
        body.put("files", resolvedFiles);
        return body;
    }

    public Map<String, Object> startDelete(
            String tenantId,
            String sourceType,
            String knowledgeName,
            String sourceCollection,
            String taskQueue,
            String pipelineId) throws JsonProcessingException {

        String target = knowledgeName == null ? "" : knowledgeName.trim();
        if (target.isEmpty()) {
            return Map.of("success", false, "message", "knowledgeName is required");
        }

        String runId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        String effectiveTenant = tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
        String effectiveSourceType = sourceType == null || sourceType.isBlank() ? "files" : sourceType.trim();
        String effectiveSourceCollection = sourceCollection == null ? "" : sourceCollection.trim();
        String effectiveQueue = taskQueue == null || taskQueue.isBlank()
                ? (ragDeleteQueue.isBlank() ? defaultTaskQueue : ragDeleteQueue)
                : taskQueue.trim();
        String effectivePipeline = pipelineId == null || pipelineId.isBlank()
                ? defaultDeletePipeline
                : pipelineId.trim();

        runStore.put(new ChatRunStore.RunRecord(
                runId,
                "rag-delete",
                runId,
                effectiveTenant,
                correlationId,
                null,
                null,
                null));
        knowledgeSourceStore.markDeleteStarted(runId, target);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "delete-rag");
        payload.put("sourceType", effectiveSourceType);
        payload.put("knowledgeName", target);
        payload.put("ragTag", target);
        if (!effectiveSourceCollection.isBlank()) {
            payload.put("sourceCollection", effectiveSourceCollection);
        }
        String payloadJson = objectMapper.writeValueAsString(payload);

        eventHandler.appendEvent(
                runId,
                "root",
                null,
                "SYSTEM",
                "STARTED",
                Map.of("type", "rag-delete",
                        "sourceType", effectiveSourceType,
                        "knowledgeName", target),
                null,
                Map.of("tenantId", effectiveTenant,
                        "sourceType", effectiveSourceType,
                        "knowledgeName", target),
                null,
                null,
                EventType.NODE_STARTED,
                correlationId);

        WorkflowInput workflowInput = WorkflowInputSerializer.buildRagDelete(
                effectiveTenant,
                target,
                payloadJson,
                effectivePipeline,
                runId,
                runId,
                callbackBaseUrl,
                correlationId);

        workflowStarter.startWorkflow(runId, workflowInput, effectiveQueue);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("runId", runId);
        body.put("sourceType", effectiveSourceType);
        body.put("knowledgeName", target);
        body.put("sourceCollection", effectiveSourceCollection);
        body.put("pipeline", effectivePipeline);
        body.put("taskQueue", effectiveQueue);
        return body;
    }

    public List<Map<String, Object>> listKnowledgeSources() {
        return knowledgeSourceStore.list();
    }

    public List<Map<String, Object>> listKnowledgeSourceCollections() {
        return resourceUploadService.listCapabilitySources();
    }

    public List<Map<String, Object>> listDocuments(String capabilitySource) {
        return resourceUploadService.listUploadedFiles(capabilitySource);
    }
}
