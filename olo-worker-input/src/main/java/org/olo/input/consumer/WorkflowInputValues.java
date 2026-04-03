/*
 * Copyright (c) 2026 Olo Labs. All rights reserved.
 */

package org.olo.input.consumer;

import java.util.Optional;

/**
 * Consumer contract: read-only access to workflow input values.
 * <p>
 * Workflows and activities use this interface to read inputs by name. They cannot modify the payload.
 * Values are resolved transparently from LOCAL (inline), CACHE (e.g. Redis), or FILE storage—
 * the consumer simply calls {@link #getStringValue(String)} and receives the value regardless of where it is stored.
 */
public interface WorkflowInputValues {

    /**
     * Returns the string value for the given input name. Resolves from LOCAL value, CACHE key, or FILE content as appropriate.
     *
     * @param inputName the input name (e.g. "input1")
     * @return the value, or empty if not found or not a string-compatible type
     */
    Optional<String> getStringValue(String inputName);

    /**
     * Returns the numeric value for the given input name. Resolves storage the same way as {@link #getStringValue(String)} then parses as number.
     *
     * @param inputName the input name
     * @return the value, or empty if not found or not a number
     */
    Optional<Double> getNumberValue(String inputName);

    /**
     * Returns the boolean value for the given input name. Resolves storage then parses as boolean ("true"/"false").
     *
     * @param inputName the input name
     * @return the value, or empty if not found or not a boolean
     */
    Optional<Boolean> getBooleanValue(String inputName);

    /**
     * Returns the file content as a string for the given input name. For FILE storage, reads the file and returns its content.
     *
     * @param inputName the input name
     * @return the file content as string, or empty if not found or not a file input
     */
    Optional<String> getFileContentAsString(String inputName);

    /**
     * Returns the raw file bytes for the given input name. For FILE storage, reads the file and returns its content.
     *
     * @param inputName the input name
     * @return the file bytes, or empty if not found or not a file input
     */
    Optional<byte[]> getFileContent(String inputName);
}
