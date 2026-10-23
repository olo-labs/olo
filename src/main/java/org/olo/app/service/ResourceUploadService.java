/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.service;

import jakarta.servlet.http.HttpServletRequest;
import org.olo.app.tenant.TenantScopedName;
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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw uploaded files, stored per tenant (M3.11).
 *
 * <p>Callers pass the verified tenant and a plain capability source name. Files live in
 * {@code <baseDir>/<TenantScopedName.qualify(tenant, source)>/}, so one tenant can never read, list or delete another
 * tenant's files, and the worker (which mounts {@code baseDir}) sees the same tenant-qualified folder names it is
 * given in workflow input.
 */
@Service
public class ResourceUploadService {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadService.class);

    private final Path baseDir;

    public ResourceUploadService(@Value("${olo.resource.upload.base-dir:${java.io.tmpdir}/olo-resource-uploads}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    public Map<String, Object> saveUpload(
            HttpServletRequest request,
            String tenantId,
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
                "resource upload start: remote={} requestId={} tenant={} capabilitySource={} fileCount={} taskQueue={} pipelineId={} baseDir={}",
                remote,
                reqId,
                tenantId,
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

        Path destRoot = sourceDir(tenantId, resolvedCapabilitySource);
        if (destRoot == null) {
            return Map.of("success", false, "message", "Invalid capabilitySource");
        }
        try {
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
            Path target = destRoot.resolve(safeName).normalize();
            if (!target.startsWith(destRoot)) {
                return Map.of("success", false, "message", "Invalid file name " + safeName);
            }
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

    /** The tenant's capability sources, by plain name. Other tenants' folders are never listed. */
    public List<Map<String, Object>> listCapabilitySources(String tenantId) {
        try {
            if (!Files.isDirectory(baseDir)) {
                return List.of();
            }
            List<Map<String, Object>> sources = new ArrayList<>();
            try (var stream = Files.list(baseDir)) {
                for (Path child : stream.filter(Files::isDirectory).toList()) {
                    Optional<String> name = TenantScopedName.unqualify(tenantId, child.getFileName().toString());
                    if (name.isEmpty()) {
                        continue;
                    }
                    List<Map<String, Object>> files = listUploadedFiles(tenantId, name.get());
                    sources.add(Map.of(
                            "capabilitySource", name.get(),
                            "fileCount", files.size(),
                            "files", files));
                }
            }
            sources.sort((a, b) -> String.valueOf(a.get("capabilitySource"))
                    .compareToIgnoreCase(String.valueOf(b.get("capabilitySource"))));
            return sources;
        } catch (IOException e) {
            log.warn("listCapabilitySources failed: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> listUploadedFiles(String tenantId, String capabilitySource) {
        if (capabilitySource == null || capabilitySource.isBlank()) {
            return List.of();
        }
        try {
            Path dir = sourceDir(tenantId, capabilitySource);
            if (dir == null || !Files.isDirectory(dir)) {
                return List.of();
            }
            List<Map<String, Object>> files = new ArrayList<>();
            try (var stream = Files.list(dir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (name.startsWith(".")) {
                        continue;
                    }
                    files.add(Map.of(
                            "fileName", name,
                            "capabilitySource", capabilitySource.trim(),
                            "sizeBytes", Files.size(file),
                            "lastModified", Files.getLastModifiedTime(file).toMillis()));
                }
            }
            files.sort((a, b) -> String.valueOf(a.get("fileName"))
                    .compareToIgnoreCase(String.valueOf(b.get("fileName"))));
            return files;
        } catch (IOException e) {
            log.warn("listUploadedFiles failed for {}: {}", capabilitySource, e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> deleteCapabilitySource(String tenantId, String capabilitySource) {
        if (capabilitySource == null || capabilitySource.isBlank()) {
            return Map.of("success", false, "message", "capabilitySource is required");
        }
        Path dir = sourceDir(tenantId, capabilitySource);
        if (dir == null) {
            return Map.of("success", false, "message", "Invalid capabilitySource");
        }
        if (!Files.exists(dir)) {
            return Map.of("success", true, "capabilitySource", capabilitySource.trim(), "deletedFiles", 0);
        }
        if (!Files.isDirectory(dir)) {
            return Map.of("success", false, "message", "Capability source is not a directory");
        }
        try {
            long deletedFiles;
            try (var countStream = Files.walk(dir)) {
                deletedFiles = countStream.filter(Files::isRegularFile).count();
            }
            try (var deleteStream = Files.walk(dir)) {
                for (Path path : deleteStream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
            log.info("resource upload source deleted: capabilitySource={} path={} deletedFiles={}",
                    capabilitySource, dir, deletedFiles);
            return Map.of(
                    "success", true,
                    "capabilitySource", capabilitySource.trim(),
                    "deletedFiles", deletedFiles);
        } catch (IOException e) {
            log.warn("deleteCapabilitySource failed for {}: {}", capabilitySource, e.getMessage());
            return Map.of(
                    "success", false,
                    "message", "Failed to delete source " + capabilitySource.trim() + ": " + e.getMessage());
        }
    }

    public Path resolveUploadedFile(String tenantId, String capabilitySource, String fileName) {
        if (capabilitySource == null || capabilitySource.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path dir = sourceDir(tenantId, capabilitySource);
        if (dir == null) {
            return null;
        }
        Path file = dir.resolve(safeFileName(fileName)).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            return null;
        }
        return file;
    }

    /** The tenant's folder for a plain source name, or {@code null} if it would fall outside {@code baseDir}. */
    private Path sourceDir(String tenantId, String capabilitySource) {
        String stored = safeCapabilitySegment(TenantScopedName.qualify(tenantId, capabilitySource));
        Path dir = baseDir.resolve(stored).normalize();
        return dir.startsWith(baseDir) && !dir.equals(baseDir) ? dir : null;
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
