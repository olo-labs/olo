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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans {@code olo.configuration.dir} for workflow JSON: regional subfolders or a flat folder
 * (treated as region {@code current-active}). Within each region, {@code *.json} files are
 * discovered recursively (e.g. {@code current-active/agents/agent.json}).
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
        try (Stream<Path> walk = Files.walk(regionDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> regionDir.relativize(path).toString().replace('\\', '/')))
                    .forEach(file -> {
                        WorkflowDefinition workflow = parseWorkflowFile(file);
                        if (workflow != null) {
                            workflows.add(workflow);
                        }
                    });
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
        boolean configuredProvided = configuredPath != null && !configuredPath.isBlank();
        Path configured = configuredProvided ? resolveConfiguredPath(configuredPath) : null;
        if (configured != null && hasLoadableWorkflows(configured)) {
            return configured;
        }
        if (shouldDiscoverFallback(configuredPath, configured)) {
            Path discovered = discoverMonorepoConfigurationRoot();
            if (discovered != null) {
                if (configured != null && Files.isDirectory(configured)) {
                    log.warn(
                            "Configured olo.configuration.dir has no workflow JSON ({}); using {}",
                            configured,
                            discovered);
                } else if (configuredProvided) {
                    log.warn(
                            "Configured olo.configuration.dir not found ({}); using {}",
                            configuredPath.trim(),
                            discovered);
                } else {
                    log.info("Discovered olo.configuration.dir at {}", discovered);
                }
                return discovered;
            }
        }
        if (configured != null && Files.isDirectory(configured)) {
            return configured;
        }
        return null;
    }

    private static boolean shouldDiscoverFallback(String configuredPath, Path configured) {
        if (configured != null && Files.isDirectory(configured)) {
            return true;
        }
        if (configuredPath == null || configuredPath.isBlank()) {
            return true;
        }
        String normalized = configuredPath.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("olo-configuration")
                || normalized.contains("olo-mono")
                || normalized.contains("current-active")
                || normalized.contains("default");
    }

    private static Path resolveConfiguredPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path path = Path.of(configuredPath.trim());
        if (path.isAbsolute()) {
            return Files.isDirectory(path) ? path.normalize() : null;
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
        return Files.isDirectory(path) ? path.normalize() : null;
    }

    private static Path discoverMonorepoConfigurationRoot() {
        Path cursor = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (cursor != null) {
            for (String relative : MONOREPO_CONFIGURATION_CANDIDATES) {
                Path candidate = cursor.resolve(relative).normalize();
                if (hasLoadableWorkflows(candidate)) {
                    return candidate;
                }
            }
            Path nestedMono = cursor.resolve("olo-mono");
            if (Files.isDirectory(nestedMono)) {
                for (String relative : MONOREPO_CONFIGURATION_CANDIDATES) {
                    Path candidate = nestedMono.resolve(relative.substring("olo-mono/".length())).normalize();
                    if (hasLoadableWorkflows(candidate)) {
                        return candidate;
                    }
                }
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static final List<String> MONOREPO_CONFIGURATION_CANDIDATES = List.of(
            "olo-mono/olo-definition/olo-configuration/current-active",
            "olo-mono/olo-definition/olo-configuration/default");

    static boolean hasLoadableWorkflows(Path rootDir) {
        if (rootDir == null || !Files.isDirectory(rootDir)) {
            return false;
        }
        if (containsWorkflowJson(rootDir)) {
            return true;
        }
        try (Stream<Path> children = Files.list(rootDir)) {
            return children
                    .filter(Files::isDirectory)
                    .anyMatch(regionDir -> containsWorkflowJson(regionDir));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsWorkflowJson(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.anyMatch(
                    path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }
}
