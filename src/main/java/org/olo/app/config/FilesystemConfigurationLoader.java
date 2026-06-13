/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.olo.definition.serializer.JsonWorkflowSerializer;
import org.olo.definition.workflow.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans {@code olo.configuration.dir} for workflow JSON: either regional subfolders or a flat
 * folder of {@code *.json} files (treated as region {@code current-active}).
 */
public final class FilesystemConfigurationLoader {

    private static final Logger log = LoggerFactory.getLogger(FilesystemConfigurationLoader.class);
    private static final JsonWorkflowSerializer WORKFLOW_SERIALIZER = new JsonWorkflowSerializer();

    private FilesystemConfigurationLoader() {
    }

    public static Map<String, RegionalConfigurationSnapshot> load(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            log.warn("Configuration directory does not exist or is not a directory: {}", rootDir);
            return Map.of();
        }
        Map<String, RegionalConfigurationSnapshot> regions = loadRegionSubfolders(rootDir);
        if (!regions.isEmpty()) {
            return regions;
        }
        // Flat layout: workflow JSON files live directly in the folder (e.g. current-active/).
        try {
            RegionalConfigurationSnapshot flat = loadRegion("current-active", rootDir);
            if (flat != null) {
                log.info("Loaded flat configuration folder as region=current-active workflows={}",
                        flat.getWorkflows().size());
                return Map.of("current-active", flat);
            }
        } catch (IOException e) {
            log.warn("Failed to load flat configuration from {}: {}", rootDir, e.getMessage());
        }
        return Map.of();
    }

    private static Map<String, RegionalConfigurationSnapshot> loadRegionSubfolders(Path rootDir) {
        Map<String, RegionalConfigurationSnapshot> out = new LinkedHashMap<>();
        try (Stream<Path> children = Files.list(rootDir)) {
            children.filter(Files::isDirectory)
                    .sorted()
                    .forEach(regionDir -> {
                        String region = regionDir.getFileName().toString();
                        if (region.startsWith(".")) {
                            return;
                        }
                        try {
                            RegionalConfigurationSnapshot snap = loadRegion(region, regionDir);
                            if (snap != null) {
                                out.put(region, snap);
                                log.info("Loaded regional configuration region={} workflows={}",
                                        region, snap.getWorkflows().size());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load regional configuration for {}: {}", region, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan configuration directory {}: {}", rootDir, e.getMessage());
        }
        return out;
    }

    static RegionalConfigurationSnapshot loadRegion(String region, Path regionDir) throws IOException {
        List<WorkflowDefinition> workflows = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.json")) {
            for (Path file : stream) {
                WorkflowDefinition workflow = parseWorkflowFile(file);
                if (workflow != null) {
                    workflows.add(workflow);
                }
            }
        }
        if (workflows.isEmpty()) {
            log.debug("No workflow definitions in region folder {}", regionDir);
            return null;
        }
        workflows.sort(Comparator.comparing(w -> w.getId() != null ? w.getId() : ""));
        return new RegionalConfigurationSnapshot(region, workflows);
    }

    private static WorkflowDefinition parseWorkflowFile(Path file) {
        try (var in = Files.newInputStream(file)) {
            WorkflowDefinition workflow = WORKFLOW_SERIALIZER.deserialize(in);
            if (workflow.getId() == null || workflow.getId().isBlank()) {
                log.warn("Skipping workflow file without id: {}", file);
                return null;
            }
            return workflow;
        } catch (Exception e) {
            log.warn("Skipping invalid workflow JSON {}: {}", file, e.getMessage());
            return null;
        }
    }

    public static Path resolveConfigurationRoot(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path path = Path.of(configuredPath.trim());
        if (path.isAbsolute() && Files.isDirectory(path)) {
            return path.normalize();
        }
        Path fromCwd = Path.of(System.getProperty("user.dir", ".")).resolve(path).normalize();
        if (Files.isDirectory(fromCwd)) {
            return fromCwd;
        }
        Path fromParent = Path.of(System.getProperty("user.dir", ".")).getParent();
        if (fromParent != null) {
            Path sibling = fromParent.resolve(path).normalize();
            if (Files.isDirectory(sibling)) {
                return sibling;
            }
        }
        if (Files.isDirectory(path)) {
            return path.normalize();
        }
        return fromCwd;
    }
}
