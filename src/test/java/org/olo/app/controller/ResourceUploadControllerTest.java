/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.service.RagIngestService;
import org.olo.app.service.ResourceUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceUploadControllerTest {

    @Test
    void uploadTriggersKnowledgeRefreshAfterSavingFiles() throws Exception {
        ResourceUploadService resourceUploadService = mock(ResourceUploadService.class);
        RagIngestService ragIngestService = mock(RagIngestService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        Map<String, Object> uploadResult = new LinkedHashMap<>();
        uploadResult.put("success", true);
        uploadResult.put("files", List.of(Map.of("fileName", "notes.pdf")));
        when(resourceUploadService.saveUpload(any(), eq("rag-knowledge"), any(), eq(null), eq(null)))
                .thenReturn(uploadResult);
        when(ragIngestService.startIngest(eq("default"), eq("rag-knowledge"), eq(List.of("notes.pdf")), eq(null), eq(null)))
                .thenReturn(Map.of("success", true, "runId", "run-1"));

        ResourceUploadController controller = new ResourceUploadController(resourceUploadService, ragIngestService);
        MultipartFile[] files = {new MockMultipartFile("files", "notes.pdf", "application/pdf", new byte[] {1, 2, 3})};

        ResponseEntity<Map<String, Object>> response = controller.upload(request, "rag-knowledge", null, null, null, files);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("success")));
        assertTrue(response.getBody().containsKey("ingest"));
    }
}
