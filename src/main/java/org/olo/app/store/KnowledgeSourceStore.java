/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.store;

import org.olo.app.domain.NodeStatus;
import org.olo.app.domain.OloExecutionEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory list of executed knowledge-source creation runs. Demo only.
 */
public class KnowledgeSourceStore {

    public static class KnowledgeSourceRecord {
        public final String runId;
        public final String tenantId;
        public final String sourceType;
        public final String capabilitySource;
        public final String knowledgeName;
        public final List<String> fileNames;
        public final String pipeline;
        public final String taskQueue;
        public final long createdAt;
        public volatile long updatedAt;
        public volatile String status;
        public volatile String message;

        public KnowledgeSourceRecord(String runId,
                                     String tenantId,
                                     String sourceType,
                                     String capabilitySource,
                                     String knowledgeName,
                                     List<String> fileNames,
                                     String pipeline,
                                     String taskQueue) {
            this.runId = runId;
            this.tenantId = tenantId;
            this.sourceType = sourceType;
            this.capabilitySource = capabilitySource;
            this.knowledgeName = knowledgeName;
            this.fileNames = List.copyOf(fileNames == null ? List.of() : fileNames);
            this.pipeline = pipeline;
            this.taskQueue = taskQueue;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
            this.status = "in_progress";
            this.message = "Indexing in progress";
        }
    }

    private final Map<String, KnowledgeSourceRecord> byRunId = new ConcurrentHashMap<>();

    public void putStarted(KnowledgeSourceRecord record) {
        byRunId.put(record.runId, record);
    }

    public void updateFromEvent(String runId, OloExecutionEvent event, String runStatus) {
        KnowledgeSourceRecord record = byRunId.get(runId);
        if (record == null) {
            return;
        }
        record.status = toKnowledgeStatus(runStatus, event);
        record.message = eventMessage(event, record.status);
        record.updatedAt = System.currentTimeMillis();
    }

    public List<Map<String, Object>> list() {
        List<KnowledgeSourceRecord> records = new ArrayList<>(byRunId.values());
        records.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return records.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(KnowledgeSourceRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", record.runId);
        row.put("tenantId", record.tenantId);
        row.put("sourceType", record.sourceType);
        row.put("capabilitySource", record.knowledgeName);
        row.put("displayName", record.knowledgeName);
        row.put("sourceCollection", record.capabilitySource);
        row.put("fileCount", record.fileNames.size());
        row.put("files", record.fileNames.stream().map(fileName -> {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("fileName", fileName);
            file.put("capabilitySource", record.capabilitySource);
            return file;
        }).toList());
        row.put("status", record.status);
        row.put("message", record.message);
        row.put("createdAt", record.createdAt);
        row.put("updatedAt", record.updatedAt);
        row.put("pipeline", record.pipeline);
        row.put("taskQueue", record.taskQueue);
        return row;
    }

    private static String toKnowledgeStatus(String runStatus, OloExecutionEvent event) {
        if ("completed".equalsIgnoreCase(runStatus)) {
            return "success";
        }
        if ("failed".equalsIgnoreCase(runStatus) || "cancelled".equalsIgnoreCase(runStatus)) {
            return "failed";
        }
        if (event != null && event.getStatus() == NodeStatus.FAILED) {
            return "failed";
        }
        return "in_progress";
    }

    private static String eventMessage(OloExecutionEvent event, String status) {
        if ("success".equals(status)) {
            return "Indexing completed";
        }
        if ("failed".equals(status)) {
            return "Indexing failed";
        }
        if (event == null || event.getStatus() == null) {
            return "Indexing in progress";
        }
        return switch (event.getStatus()) {
            case WAITING -> "Waiting for workflow";
            case COMPLETED -> "Workflow step completed";
            case FAILED -> "Indexing failed";
            case STARTED -> "Indexing in progress";
        };
    }
}
