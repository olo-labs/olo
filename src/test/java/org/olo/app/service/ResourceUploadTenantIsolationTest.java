/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M3.11: uploaded files are visible, downloadable and deletable only within their tenant.
 */
class ResourceUploadTenantIsolationTest {

    @TempDir
    Path baseDir;

    private ResourceUploadService service;

    @BeforeEach
    void setUp() {
        service = new ResourceUploadService(baseDir.toString());
        upload("t1", "finance", "q3.pdf");
        upload("t2", "finance", "secret.pdf");
    }

    private void upload(String tenant, String source, String fileName) {
        Map<String, Object> result = service.saveUpload(new MockHttpServletRequest(), tenant, source,
                List.of(new MockMultipartFile("files", fileName, "application/pdf", new byte[] {1, 2, 3})), null, null);
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    void sameSourceNameInTwoTenantsIsTwoSeparateFolders() {
        assertThat(Files.isRegularFile(baseDir.resolve("2-t1-finance").resolve("q3.pdf"))).isTrue();
        assertThat(Files.isRegularFile(baseDir.resolve("2-t2-finance").resolve("secret.pdf"))).isTrue();
    }

    @Test
    void listingShowsOnlyTheCallersSourcesByPlainName() {
        List<Map<String, Object>> t1 = service.listCapabilitySources("t1");

        assertThat(t1).extracting(m -> m.get("capabilitySource")).containsExactly("finance");
        assertThat(service.listUploadedFiles("t1", "finance")).extracting(m -> m.get("fileName")).containsExactly("q3.pdf");
        assertThat(service.listCapabilitySources("t3")).isEmpty();
    }

    @Test
    void anotherTenantsFileCanNotBeDownloaded() {
        assertThat(service.resolveUploadedFile("t1", "finance", "q3.pdf")).isNotNull();
        assertThat(service.resolveUploadedFile("t1", "finance", "secret.pdf")).isNull();
        assertThat(service.resolveUploadedFile("t1", "2-t2-finance", "secret.pdf")).isNull();
    }

    @Test
    void deletingASourceNeverTouchesAnotherTenant() {
        assertThat(service.deleteCapabilitySource("t1", "finance").get("deletedFiles")).isEqualTo(1L);

        assertThat(service.listCapabilitySources("t1")).isEmpty();
        assertThat(service.listUploadedFiles("t2", "finance")).hasSize(1);
    }

    @Test
    void pathTraversalStaysInsideTheTenantsFolder() {
        assertThat(service.resolveUploadedFile("t1", "..", "q3.pdf")).isNull();
        assertThat(service.resolveUploadedFile("t1", "finance", "../2-t2-finance/secret.pdf")).isNull();
        assertThat(service.deleteCapabilitySource("t1", "..").get("deletedFiles")).isEqualTo(0);
        assertThat(service.listUploadedFiles("t2", "finance")).hasSize(1);
    }
}
