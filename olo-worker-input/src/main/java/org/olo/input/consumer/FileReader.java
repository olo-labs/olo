/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.consumer;

import java.util.Optional;

/**
 * Abstraction for reading file content by relative folder and file name.
 * The consumer (worker) provides an implementation (e.g. reading from a base directory or S3); the input module only depends on this contract.
 */
public interface FileReader {

    /**
     * Reads the file content as a string.
     *
     * @param relativeFolder relative folder path (e.g. "rag/8huqpd42mizzgjOhJEH9C/")
     * @param fileName       file name (e.g. "readme.md")
     * @return the file content, or empty if not found or on error
     */
    Optional<String> readAsString(String relativeFolder, String fileName);

    /**
     * Reads the file content as raw bytes.
     *
     * @param relativeFolder relative folder path
     * @param fileName       file name
     * @return the file bytes, or empty if not found or on error
     */
    Optional<byte[]> readAsBytes(String relativeFolder, String fileName);
}
