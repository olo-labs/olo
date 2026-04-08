/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

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
 * Legacy {@code /api/rag/upload} delegates here with the same behaviour.
 */
@RestController
@Tag(name = "Resource upload", description = "Upload raw files for capability sources (shared folder / future object storage)")
public class ResourceUploadController {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadController.class);

    private final ResourceUploadService resourceUploadService;

    public ResourceUploadController(ResourceUploadService resourceUploadService) {
        this.resourceUploadService = resourceUploadService;
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
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Upload resource files (legacy path)", description = "Same as POST /api/resource/upload; form field ragId accepted")
    @PostMapping(value = "/api/rag/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadLegacy(
            HttpServletRequest request,
            @RequestParam(value = "capabilitySource", required = false) String capabilitySource,
            @RequestParam(value = "ragId", required = false) String ragId,
            @RequestParam(value = "taskQueue", required = false) String taskQueue,
            @RequestParam(value = "pipelineId", required = false) String pipelineId,
            @RequestParam("files") MultipartFile[] files) {

        log.info("POST /api/rag/upload (legacy) delegating to resource upload handler");
        return upload(request, capabilitySource, ragId, taskQueue, pipelineId, files);
    }

    public record ReprocessRequest(String capabilitySource, String ragId, String fileName) {}

    @Operation(summary = "Reprocess uploaded file", description = "JSON: capabilitySource (or legacy ragId), fileName")
    @PostMapping(value = "/api/resource/reprocess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reprocess(@RequestBody ReprocessRequest body) {
        String src = body.capabilitySource();
        if (src == null || src.isBlank()) {
            src = body.ragId();
        }
        log.info("POST /api/resource/reprocess capabilitySource={} fileName={}", src, body.fileName());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping(value = "/api/rag/reprocess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> reprocessLegacy(@RequestBody ReprocessRequest body) {
        log.info("POST /api/rag/reprocess (legacy)");
        return reprocess(body);
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
