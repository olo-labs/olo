/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.service.RagIngestService;
import org.olo.app.service.ResourceUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Multipart resource upload for the Documents UI ({@code POST /api/resource/upload}).
 */
@RestController
@Tag(name = "Resource upload", description = "Upload raw files for capability sources (shared folder / future object storage)")
public class ResourceUploadController {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadController.class);

    private final ResourceUploadService resourceUploadService;
    private final RagIngestService ragIngestService;

    public ResourceUploadController(ResourceUploadService resourceUploadService,
                                    RagIngestService ragIngestService) {
        this.resourceUploadService = resourceUploadService;
        this.ragIngestService = ragIngestService;
    }

    @Operation(summary = "Upload resource files", description = "Multipart: capabilitySource, files; optional taskQueue, pipelineId")
    @PostMapping(value = "/api/resource/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            HttpServletRequest request,
            @RequestParam(value = "capabilitySource", required = false) String capabilitySource,
            @RequestParam(value = "ragId", required = false) String ragId,
            @RequestParam(value = "taskQueue", required = false) String taskQueue,
            @RequestParam(value = "pipelineId", required = false) String pipelineId,
            @RequestParam("files") MultipartFile[] files) {

        log.info(
                "POST /api/resource/upload invoked: Content-Type={} contentLength={}",
                request.getContentType(),
                request.getContentLengthLong());

        String resolved = resolveCapabilitySource(capabilitySource, ragId);
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        Map<String, Object> body = resourceUploadService.saveUpload(request, resolved, list, taskQueue, pipelineId);
        boolean ok = Boolean.TRUE.equals(body.get("success"));
        if (!ok) {
            return ResponseEntity.badRequest().body(body);
        }
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> savedFiles = (List<Map<String, String>>) body.getOrDefault("files", List.of());
            List<String> fileNames = savedFiles.stream()
                    .map(entry -> entry == null ? null : entry.get("fileName"))
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
            if (!fileNames.isEmpty()) {
                Map<String, Object> ingest = ragIngestService.startIngest(
                        "default",
                        resolved,
                        fileNames,
                        taskQueue,
                        pipelineId);
                body.put("ingest", ingest);
            }
        } catch (Exception e) {
            log.warn("resource upload succeeded but ingest kickoff failed for capabilitySource={}: {}", resolved, e.getMessage(), e);
            body.put("ingest", Map.of(
                    "success", false,
                    "message", "Upload saved, but knowledge refresh failed: " + e.getMessage()));
        }
        return ResponseEntity.ok(body);
    }

    public record ReprocessRequest(String capabilitySource, String ragId, String fileName) {}

    @Operation(summary = "Reprocess uploaded file", description = "JSON: capabilitySource (or legacy ragId), fileName")
    @PostMapping(value = "/api/resource/reprocess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reprocess(@RequestBody ReprocessRequest body) throws Exception {
        String src = body.capabilitySource();
        if (src == null || src.isBlank()) {
            src = body.ragId();
        }
        log.info("POST /api/resource/reprocess capabilitySource={} fileName={}", src, body.fileName());
        List<String> files = body.fileName() == null || body.fileName().isBlank()
                ? List.of()
                : List.of(body.fileName().trim());
        Map<String, Object> result = ragIngestService.startIngest(
                "default",
                src,
                files,
                null,
                null);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    private static String resolveCapabilitySource(String capabilitySource, String ragId) {
        if (capabilitySource != null && !capabilitySource.isBlank()) {
            return capabilitySource.trim();
        }
        if (ragId != null && !ragId.isBlank()) {
            return ragId.trim();
        }
        return "";
    }
}
