/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceUploadService {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadService.class);

    private final Path baseDir;

    public ResourceUploadService(@Value("${olo.resource.upload.base-dir:${java.io.tmpdir}/olo-resource-uploads}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> saveUpload(
            HttpServletRequest request,
            String resolvedCapabilitySource,
            List<MultipartFile> files,
            String taskQueue,
            String pipelineId) {

        String remote = request.getRemoteAddr();
        String reqId = request.getHeader("X-Request-Id");
        if (reqId == null || reqId.isBlank()) {
            reqId = "-";
        }

        log.info(
                "resource upload start: remote={} requestId={} capabilitySource={} fileCount={} taskQueue={} pipelineId={} baseDir={}",
                remote,
                reqId,
                resolvedCapabilitySource,
                files == null ? 0 : files.size(),
                taskQueue == null || taskQueue.isBlank() ? "-" : taskQueue,
                pipelineId == null || pipelineId.isBlank() ? "-" : pipelineId,
                baseDir);

        if (resolvedCapabilitySource == null || resolvedCapabilitySource.isBlank()) {
            log.warn("resource upload rejected: missing capabilitySource/ragId (remote={} requestId={})", remote, reqId);
            return Map.of("success", false, "message", "capabilitySource is required");
        }

        if (files == null || files.isEmpty()) {
            log.warn(
                    "resource upload rejected: no files (remote={} requestId={} capabilitySource={})",
                    remote,
                    reqId,
                    resolvedCapabilitySource);
            return Map.of("success", false, "message", "At least one file is required");
        }

        Path destRoot;
        try {
            destRoot = baseDir.resolve(safeCapabilitySegment(resolvedCapabilitySource));
            Files.createDirectories(destRoot);
            log.info("resource upload target directory ready: {}", destRoot);
        } catch (IOException e) {
            log.error(
                    "resource upload failed: could not create directory under baseDir={} capabilitySource={}",
                    baseDir,
                    resolvedCapabilitySource,
                    e);
            return Map.of(
                    "success",
                    false,
                    "message",
                    "Could not create upload directory: " + e.getMessage());
        }

        List<Map<String, String>> fileEntries = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile mf = files.get(i);
            if (mf == null || mf.isEmpty()) {
                log.warn("resource upload skip empty part index={} (remote={} requestId={})", i, remote, reqId);
                continue;
            }
            String originalName = mf.getOriginalFilename();
            long size = mf.getSize();
            String contentType = mf.getContentType();
            log.info(
                    "resource upload part index={} originalFilename={} size={} contentType={}",
                    i,
                    originalName,
                    size,
                    contentType);

            String safeName = safeFileName(originalName);
            Path target = destRoot.resolve(safeName);
            try {
                try (InputStream in = mf.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("resource upload saved: {}", target);
                fileEntries.add(Map.of("fileName", safeName));
            } catch (IOException e) {
                log.error(
                        "resource upload failed writing file: target={} originalFilename={} remote={}",
                        target,
                        originalName,
                        remote,
                        e);
                return Map.of(
                        "success",
                        false,
                        "message",
                        "Failed to save file " + safeName + ": " + e.getMessage());
            }
        }

        if (fileEntries.isEmpty()) {
            log.warn(
                    "resource upload rejected: all file parts empty (remote={} requestId={} capabilitySource={})",
                    remote,
                    reqId,
                    resolvedCapabilitySource);
            return Map.of("success", false, "message", "All file parts were empty");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("files", fileEntries);
        log.info(
                "resource upload done: remote={} requestId={} capabilitySource={} savedCount={}",
                remote,
                reqId,
                resolvedCapabilitySource,
                fileEntries.size());
        return body;
    }

    static String safeCapabilitySegment(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return "_";
        }
        return t.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "unnamed.bin";
        }
        Path p = Paths.get(original).getFileName();
        String n = p == null ? original : p.toString();
        if (n.contains("..") || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0) {
            return "invalid-name";
        }
        return n.isBlank() ? "unnamed.bin" : n;
    }
}
