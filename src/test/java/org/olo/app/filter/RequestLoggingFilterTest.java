// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {
    @Test
    void logsMetadataWithoutReadingOrRecordingPrivateRequestData(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST","/api/v1/conversations/test/turns");
        request.setContent("private-body-canary".getBytes(StandardCharsets.UTF_8));
        request.setQueryString("query=private-query-canary");
        request.addHeader("Authorization","Bearer private-credential-canary");
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request,response,(incoming,outgoing) -> {
            assertThat(incoming.getInputStream().readAllBytes()).isEqualTo("private-body-canary".getBytes(StandardCharsets.UTF_8));
            outgoing.getOutputStream().write("response preserved".getBytes(StandardCharsets.UTF_8));
        });
        assertThat(response.getContentAsString()).isEqualTo("response preserved");
        assertThat(output).contains("REQUEST POST /api/v1/conversations/test/turns status=200")
                .doesNotContain("private-body-canary","private-query-canary","private-credential-canary");
    }
}
