/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

/**
 * Logs root causes for upload failures (size limits, malformed multipart, etc.).
 */
@RestControllerAdvice
public class ResourceUploadExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ResourceUploadExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> onMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        log.error(
                "multipart upload rejected (max size): method={} uri={} contentLength={} — {}",
                req.getMethod(),
                req.getRequestURI(),
                req.getContentLengthLong(),
                ex.toString(),
                ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of(
                        "success",
                        false,
                        "message",
                        "File too large (exceeds server upload limit)."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> onMultipart(MultipartException ex, HttpServletRequest req) {
        log.error(
                "multipart error: method={} uri={} contentType={} — {}",
                req.getMethod(),
                req.getRequestURI(),
                req.getContentType(),
                ex.toString(),
                ex);
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "success",
                        false,
                        "message",
                        "Multipart request error: " + ex.getMessage()));
    }
}
