/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.configuration.snapshot;

import java.time.Instant;
import java.util.Map;

/**
 * Multi-block metadata for the config snapshot (stored at olo:configuration:meta:&lt;region&gt;).
 * Example: { "regionalSettings": { "version": 7, "lastUpdated": "..." }, "core": { ... }, "pipelines": { ... }, "connections": { ... } }.
 * <p><strong>regionalSettings:</strong> When this block's version changes, workers must do a <em>full</em> snapshot reload
 * (not per-section). Use for changes that affect the whole runtime (Temporal namespace, Redis, region routing, etc.).
 * </p>
 * <p><strong>Refresh:</strong> Workers track per-section versions and compare to meta.getBlocks().get(section).getVersion().
 * If regionalSettings version changed → full reload; else reload only changed sections. Never use a single "max" version for refresh.
 * </p>
 */
public final class SnapshotMetadata {

  private static final String DEFAULT_BLOCK = "core";

  private final long generation;
  private final String checksum;
  private final Map<String, BlockMetadata> blocks;

  public SnapshotMetadata(long generation, String checksum, Map<String, BlockMetadata> blocks) {
    this.generation = generation;
    this.checksum = checksum;
    this.blocks = blocks == null || blocks.isEmpty()
        ? Map.of()
        : Map.copyOf(blocks);
  }

  public SnapshotMetadata(Map<String, BlockMetadata> blocks) {
    this(0L, null, blocks);
  }

  /** Single-block constructor for backward compatibility. */
  public SnapshotMetadata(long version, Instant lastUpdated) {
    this(version, null, Map.of(DEFAULT_BLOCK, new BlockMetadata(version, lastUpdated)));
  }

  /** All named blocks (e.g. core, pipelines, connections). Use these for per-section version comparison. */
  public Map<String, BlockMetadata> getBlocks() {
    return blocks;
  }

  /** Monotonically increasing global generation for the whole snapshot. */
  public long getGeneration() {
    return generation;
  }

  /** Optional SHA-256 checksum for core + pipelines + connections canonical JSON. */
  public String getChecksum() {
    return checksum;
  }

  /** Aggregate max version across blocks (e.g. for display). Do NOT use for refresh; use per-section comparison. */
  public long getVersion() {
    return blocks.values().stream()
        .mapToLong(BlockMetadata::getVersion)
        .max()
        .orElse(0L);
  }

  /** Latest lastUpdated across all blocks. */
  public Instant getLastUpdated() {
    return blocks.values().stream()
        .map(BlockMetadata::getLastUpdated)
        .max(Instant::compareTo)
        .orElse(Instant.EPOCH);
  }
}
