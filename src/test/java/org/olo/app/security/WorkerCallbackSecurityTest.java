/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olo.app.controller.RunsController;
import org.olo.app.security.callback.CallbackSignature;
import org.olo.app.service.RunService;
import org.olo.app.store.ChatRunStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3.11: the worker run-event callback is accepted only with a valid HMAC signature, and runs stay tenant-scoped for users.
 */
@WebMvcTest(controllers = org.olo.app.knowledge.WorkerEventsController.class)
@Import({SecurityConfig.class, WorkerCallbackSecurityTest.TestKeys.class, ChatRunStore.class})
@TestPropertySource(properties = {
        "olo.security.issuer=" + TestTokens.ISSUER,
        "olo.security.audience=" + TestTokens.AUDIENCE,
        "olo.security.jwk-set-uri=http://localhost:1/jwks",
        "olo.security.store=memory",
        "olo.security.bootstrap-memberships[0].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[0].subject=alice",
        "olo.security.bootstrap-memberships[0].tenants=t1",
        "olo.worker-callback.keys[0].id=k1",
        "olo.worker-callback.keys[0].secret=" + WorkerCallbackSecurityTest.SECRET,
        "olo.worker-callback.keys[1].id=k0",
        "olo.worker-callback.keys[1].secret=" + WorkerCallbackSecurityTest.OLD_SECRET
})
class WorkerCallbackSecurityTest {

    static final String SECRET = "test-callback-secret-0123456789-abcdef";
    static final String OLD_SECRET = "older-callback-secret-0123456789-abcdef";
    private static final String BODY = "{\"sequenceNumber\":1,\"nodeId\":\"kernel\",\"nodeType\":\"SYSTEM\",\"status\":\"COMPLETED\"}";

    @TestConfiguration
    static class TestKeys {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder(OloSecurityProperties properties) {
            return TestTokens.decoder(properties);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ChatRunStore runStore;

    @MockBean
    private RunService runService;

    @BeforeEach
    void runs() {
        runStore.put(new ChatRunStore.RunRecord("run-1", "s1", "m1", "t1", "c1", null, null, null));
        runStore.put(new ChatRunStore.RunRecord("run-2", "s2", "m2", "t2", "c2", null, null, null));
    }

    private static MockHttpServletRequestBuilder signed(String path, String keyId, String secret, long timestamp, String body) {
        String signature = CallbackSignature.sign(secret.getBytes(StandardCharsets.UTF_8), timestamp, "POST", path,
                body.getBytes(StandardCharsets.UTF_8));
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(CallbackSignature.HEADER_KEY, keyId)
                .header(CallbackSignature.HEADER_TIMESTAMP, String.valueOf(timestamp))
                .header(CallbackSignature.HEADER_SIGNATURE, signature);
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    @Test
    void validSignatureIsAccepted() throws Exception {
        mvc.perform(signed("/api/runs/run-1/events", "k1", SECRET, now(), BODY)).andExpect(status().isNoContent());
        verify(runService).appendEvent(eq("run-1"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void previousKeyStillWorksDuringRotation() throws Exception {
        mvc.perform(signed("/api/runs/run-1/events", "k0", OLD_SECRET, now(), BODY)).andExpect(status().isNoContent());
    }

    @Test
    void unsignedCallbackIsRefused() throws Exception {
        mvc.perform(post("/api/runs/run-1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("unknown_key"));
        verify(runService, never()).appendEvent(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void wrongKeyTamperedBodyOrStaleTimestampIsRefused() throws Exception {
        mvc.perform(signed("/api/runs/run-1/events", "k1", "a-completely-different-secret-0123456789", now(), BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("bad_signature"));

        String signature = CallbackSignature.sign(SECRET.getBytes(StandardCharsets.UTF_8), now(), "POST",
                "/api/runs/run-1/events", BODY.getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/runs/run-1/events").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("COMPLETED", "FAILED"))
                        .header(CallbackSignature.HEADER_KEY, "k1")
                        .header(CallbackSignature.HEADER_TIMESTAMP, String.valueOf(now()))
                        .header(CallbackSignature.HEADER_SIGNATURE, signature))
                .andExpect(status().isUnauthorized());

        mvc.perform(signed("/api/runs/run-1/events", "k1", SECRET, now() - 3600, BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("stale"));
    }

    @Test
    void signatureIsBoundToThePath() throws Exception {
        String signature = CallbackSignature.sign(SECRET.getBytes(StandardCharsets.UTF_8), now(), "POST",
                "/api/runs/run-1/events", BODY.getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/api/runs/run-2/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header(CallbackSignature.HEADER_KEY, "k1")
                        .header(CallbackSignature.HEADER_TIMESTAMP, String.valueOf(now()))
                        .header(CallbackSignature.HEADER_SIGNATURE, signature))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedCallbackAfterBackendRestartIsAcknowledgedWithoutStoringContent() throws Exception {
        mvc.perform(signed("/api/runs/run-x/events", "k1", SECRET, now(), BODY)).andExpect(status().isNoContent());
    }

    @Test
    void aUserTokenCanNotPostWorkerEvents() throws Exception {
        mvc.perform(post("/api/runs/run-1/events").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer " + TestTokens.token("alice", "a1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyRunReadPathsAreClosed() throws Exception {
        String alice = "Bearer " + TestTokens.token("alice", "a2");
        mvc.perform(get("/api/runs/run-1").header("Authorization", alice)).andExpect(status().isGone());
        mvc.perform(get("/api/runs/run-2").header("Authorization", alice)).andExpect(status().isGone());
    }
}
