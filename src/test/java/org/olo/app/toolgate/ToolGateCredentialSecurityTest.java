/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.toolgate;

import org.junit.jupiter.api.Test;
import org.olo.app.security.OloSecurityProperties;
import org.olo.app.security.SecurityConfig;
import org.olo.app.security.TestTokens;
import org.olo.app.security.callback.CallbackSignature;
import org.olo.app.security.store.IdentityStore;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M3.4: only a signed worker request gets a ToolGate user credential, and only for a live session and member. */
@WebMvcTest(controllers = ToolGateCredentialController.class)
@Import({SecurityConfig.class, ToolGateCredentialSecurityTest.TestKeys.class})
@TestPropertySource(properties = {
        "olo.security.issuer=" + TestTokens.ISSUER,
        "olo.security.audience=" + TestTokens.AUDIENCE,
        "olo.security.jwk-set-uri=http://localhost:1/jwks",
        "olo.security.store=memory",
        "olo.worker-callback.keys[0].id=k1",
        "olo.worker-callback.keys[0].secret=" + ToolGateCredentialSecurityTest.SECRET
})
class ToolGateCredentialSecurityTest {

    static final String SECRET = "test-callback-secret-0123456789-abcdef";
    private static final String PATH = "/api/internal/toolgate/credentials";

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
    private IdentityStore identityStore;

    @MockBean
    private ToolGateClient toolGate;

    private String user(String subject, String sid, String tenant) {
        String userId = identityStore.userIdFor(TestTokens.ISSUER, subject);
        identityStore.addMembership(userId, tenant);
        identityStore.session(TestTokens.ISSUER, sid, userId, Instant.now().plusSeconds(300));
        return userId;
    }

    private static String ref(String tenant, String userId, String sid, long epoch) {
        return "{\"tenantId\":\"" + tenant + "\",\"userId\":\"" + userId + "\",\"issuer\":\"" + TestTokens.ISSUER
                + "\",\"sessionId\":\"" + sid + "\",\"sessionEpoch\":" + epoch + "}";
    }

    private static MockHttpServletRequestBuilder signed(String body) {
        long now = Instant.now().getEpochSecond();
        String signature = CallbackSignature.sign(SECRET.getBytes(StandardCharsets.UTF_8), now, "POST", PATH,
                body.getBytes(StandardCharsets.UTF_8));
        return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(CallbackSignature.HEADER_KEY, "k1")
                .header(CallbackSignature.HEADER_TIMESTAMP, String.valueOf(now))
                .header(CallbackSignature.HEADER_SIGNATURE, signature);
    }

    @Test
    void signedWorkerGetsACredentialForALiveSessionOfAMember() throws Exception {
        String userId = user("alice", "sid-a", "t1");
        when(toolGate.exchange(eq("t1"), eq(userId), any())).thenReturn(new ToolGateClient.Credential("tg-credential", 15));
        mvc.perform(signed(ref("t1", userId, "sid-a", 1)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("tg-credential"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(15));
    }

    @Test
    void unsignedRequestsAndUserTokensAreRefused() throws Exception {
        String userId = user("bob", "sid-b", "t1");
        String body = ref("t1", userId, "sid-b", 1);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + TestTokens.token("bob", "sid-b")))
                .andExpect(status().isUnauthorized());
        verify(toolGate, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    void endedSessionsStaleEpochsForeignUsersAndNonMembersGetNothing() throws Exception {
        String carol = user("carol", "sid-c", "t1");
        String dave = user("dave", "sid-d", "t1");
        mvc.perform(signed(ref("t1", carol, "sid-c", 2))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("session_ended"));
        mvc.perform(signed(ref("t1", carol, "sid-d", 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("session_ended"));
        mvc.perform(signed(ref("t2", carol, "sid-c", 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("not_member"));
        mvc.perform(signed(ref("t1", carol, "unknown-sid", 1))).andExpect(status().isForbidden());

        identityStore.revokeSession(TestTokens.ISSUER, "sid-d");
        verify(toolGate).revoke(dave);
        mvc.perform(signed(ref("t1", dave, "sid-d", 2))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("session_ended"));
        verify(toolGate, never()).exchange(anyString(), anyString(), any());
    }

    @Test
    void toolGateRefusalOfADisabledUserIsForbidden() throws Exception {
        String userId = user("erin", "sid-e", "t1");
        when(toolGate.exchange(eq("t1"), eq(userId), any())).thenThrow(new ToolGateClient.ToolGateException(403, "forbidden", "no"));
        mvc.perform(signed(ref("t1", userId, "sid-e", 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("user_not_enabled"));
    }

    @Test
    void anExpiredSessionCannotRenewFromItsBindingReference() throws Exception {
        String user = identityStore.userIdFor(TestTokens.ISSUER, "expired");
        identityStore.addMembership(user, "t1");
        identityStore.session(TestTokens.ISSUER, "expired-sid", user, Instant.now().minusSeconds(1));
        mvc.perform(signed(ref("t1", user, "expired-sid", 1))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("session_ended"));
        verify(toolGate, never()).exchange(eq("t1"), eq(user), any());
    }

    @Test
    void pendingRevocationsBlockNewCredentialsUntilAuthorityIsSynchronized() throws Exception {
        String user = user("pending", "pending-sid", "t1");
        identityStore.enqueueRevocation(user, "rev-1");
        mvc.perform(signed(ref("t1", user, "pending-sid", 1))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("revocation_pending"));
        verify(toolGate, never()).exchange(eq("t1"), eq(user), any());
        identityStore.acknowledgeRevocation(user, "rev-1");
    }
}
