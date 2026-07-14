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
            @Schema(description = "Upload folder key / RAG collection id", example = "finance-rag", requiredMode = Schema.RequiredMode.REQUIRED)
            String capabilitySource,
            @Schema(description = "File names to index (empty = all files in source)") List<String> fileNames,
            @Schema(description = "Temporal task queue override") String taskQueue,
            @Schema(description = "Workflow pipeline id", example = "documents-index") String pipelineId) {}

    @Operation(summary = "Start RAG ingest workflow", description = "Indexes selected uploaded files via documents-index Temporal pipeline")
    @PostMapping("/rag/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody RagIngestRequest body) throws Exception {
        Map<String, Object> result = ragIngestService.startIngest(
                body.tenantId(),
                body.capabilitySource(),
                body.fileNames(),
                body.taskQueue(),
                body.pipelineId());
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @Operation(summary = "List knowledge sources", description = "Capability sources with uploaded file counts")
    @GetMapping("/knowledge/sources")
    public ResponseEntity<List<Map<String, Object>>> listKnowledgeSources() {
        return ResponseEntity.ok(ragIngestService.listKnowledgeSources());
    }

    @Operation(summary = "List uploaded documents", description = "Files stored for a capability source")
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(
            @RequestParam(value = "capabilitySource", required = false) String capabilitySource) {
        return ResponseEntity.ok(ragIngestService.listDocuments(capabilitySource));
    }
}
