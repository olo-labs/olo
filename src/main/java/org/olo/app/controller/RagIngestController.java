/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.olo.app.service.RagIngestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "RAG ingest", description = "Create RAG indexing runs and list knowledge sources")
public class RagIngestController {

    private final RagIngestService ragIngestService;

    public RagIngestController(RagIngestService ragIngestService) {
        this.ragIngestService = ragIngestService;
    }

    @Schema(description = "Start RAG indexing for uploaded documents")
    public record RagIngestRequest(
            @Schema(description = "Tenant id", example = "default") String tenantId,
            @Schema(description = "Knowledge source type", example = "files") String sourceType,
            @Schema(description = "Upload folder key / RAG collection id", example = "finance-rag", requiredMode = Schema.RequiredMode.REQUIRED)
            String capabilitySource,
            @Schema(description = "Final tokenized knowledge source name", example = "finance-q3-index") String knowledgeName,
            @Schema(description = "File names to index (empty = all files in source)") List<String> fileNames,
            @Schema(description = "Temporal task queue override") String taskQueue,
            @Schema(description = "Workflow pipeline id", example = "documents-index") String pipelineId) {}

    @Schema(description = "Start RAG deletion for a tokenized knowledge source")
    public record RagDeleteRequest(
            @Schema(description = "Tenant id", example = "default") String tenantId,
            @Schema(description = "Knowledge source type", example = "files") String sourceType,
            @Schema(description = "Final tokenized knowledge source name", example = "finance-q3-index", requiredMode = Schema.RequiredMode.REQUIRED)
            String knowledgeName,
            @Schema(description = "Original source collection, when known", example = "finance-uploads") String sourceCollection,
            @Schema(description = "Temporal task queue override") String taskQueue,
            @Schema(description = "Delete workflow pipeline id", example = "documents-delete") String pipelineId) {}

    @Operation(summary = "Start RAG ingest workflow", description = "Indexes selected uploaded files via documents-index Temporal pipeline")
    @PostMapping("/rag/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody RagIngestRequest body) throws Exception {
        Map<String, Object> result = ragIngestService.startIngest(
                body.tenantId(),
                body.sourceType(),
                body.capabilitySource(),
                body.knowledgeName(),
                body.fileNames(),
                body.taskQueue(),
                body.pipelineId());
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @Operation(summary = "Start RAG delete workflow", description = "Deletes a tokenized knowledge source via a dedicated Temporal pipeline")
    @PostMapping("/rag/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestBody RagDeleteRequest body) throws Exception {
        Map<String, Object> result = ragIngestService.startDelete(
                body.tenantId(),
                body.sourceType(),
                body.knowledgeName(),
                body.sourceCollection(),
                body.taskQueue(),
                body.pipelineId());
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @Operation(summary = "List knowledge sources", description = "Executed knowledge sources with indexing status")
    @GetMapping("/knowledge/sources")
    public ResponseEntity<List<Map<String, Object>>> listKnowledgeSources() {
        return ResponseEntity.ok(ragIngestService.listKnowledgeSources());
    }

    @Operation(summary = "List knowledge source collections", description = "Raw uploaded source collections available for indexing")
    @GetMapping("/knowledge/source-collections")
    public ResponseEntity<List<Map<String, Object>>> listKnowledgeSourceCollections() {
        return ResponseEntity.ok(ragIngestService.listKnowledgeSourceCollections());
    }

    @Operation(summary = "List uploaded documents", description = "Files stored for a capability source")
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(
            @RequestParam(value = "capabilitySource", required = false) String capabilitySource) {
        return ResponseEntity.ok(ragIngestService.listDocuments(capabilitySource));
    }
}
