/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olo.app.security.OloSecurityProperties;
import org.olo.app.security.SecurityConfig;
import org.olo.app.security.TestTokens;
import org.olo.app.security.store.IdentityStore;
import org.olo.app.service.RunService;
import org.olo.app.store.ChatMessageStore;
import org.olo.app.store.ChatRunStore;
import org.olo.app.store.ChatSessionStore;
import org.olo.app.store.ExecutionEventStore;
import org.olo.input.model.IdentityBinding;
import org.olo.input.model.WorkflowInput;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M3.3: a chat run carries the verified user it acts for as a non-secret binding reference. */
@WebMvcTest(controllers = org.olo.app.knowledge.KnowledgeController.class)
@Import({SecurityConfig.class, RunIdentityBindingTest.TestBeans.class, ChatSessionStore.class, ChatMessageStore.class,
        ChatRunStore.class})
@TestPropertySource(properties = {
        "olo.security.issuer=" + TestTokens.ISSUER,
        "olo.security.audience=" + TestTokens.AUDIENCE,
        "olo.security.jwk-set-uri=http://localhost:1/jwks",
        "olo.security.store=memory",
        "olo.security.bootstrap-memberships[0].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[0].subject=alice",
        "olo.security.bootstrap-memberships[0].tenants=t1"
})
class RunIdentityBindingTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder(OloSecurityProperties properties) {
            return TestTokens.decoder(properties);
        }

        @Bean
        String oloTaskQueue() {
            return "olo-chat";
        }

        @Bean
        String oloCallbackBaseUrl() {
            return "http://backend.test";
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IdentityStore identityStore;

    @MockBean
    private RunService runService;

    @MockBean
    private org.olo.app.knowledge.PlatformKnowledge knowledge;

    @MockBean
    private ExecutionEventStore eventStore;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void chatRunCarriesTheVerifiedUserFromTheToken() throws Exception {
        String bearer = "Bearer " + TestTokens.token("alice", "sid-alice");
        String sessionId = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/conversations/" + sessionId + "/turns").header("Authorization", bearer)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\",\"identity\":{\"userId\":\"someone-else\"}}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<WorkflowInput> input = ArgumentCaptor.forClass(WorkflowInput.class);
        verify(runService).startWorkflow(anyString(), input.capture(), eq("olo-chat"));
        String userId = identityStore.findUserId(TestTokens.ISSUER, "alice").orElseThrow();
        assertThat(input.getValue().getContext().getIdentity())
                .isEqualTo(new IdentityBinding("t1", userId, TestTokens.ISSUER, "sid-alice", 1));

        JsonNode serialized = json.readTree(json.writeValueAsString(input.getValue())).path("context").path("identity");
        assertThat(serialized.path("userId").asText()).isEqualTo(userId);
        assertThat(serialized.path("sessionEpoch").asLong()).isEqualTo(1);
        assertThat(serialized.has("accessToken")).isFalse();
    }
    @Test
    void cancellationRebuildsItsRunBindingFromGovernedHistoryAfterRestart() throws Exception {
        String conversation=java.util.UUID.randomUUID().toString(), turn=java.util.UUID.randomUUID().toString();
        org.mockito.Mockito.when(knowledge.read(eq(conversation),org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(json.readTree("{\"messages\":[{\"role\":\"user\",\"message_id\":\"accepted-message\"}]}"));
        mvc.perform(post("/api/v1/conversations/"+conversation+"/turns/"+turn+"/cancel")
                .header("Authorization","Bearer "+TestTokens.token("alice","sid-alice"))
                .header("Idempotency-Key",java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        verify(knowledge).call(anyString(),eq("memory.append"),eq(conversation),eq(java.util.Map.of("type","check")));
        verify(runService).cancelRun(turn);
    }

    @Test
    void deniedAcceptedMessageNeverStartsAWorkflow() throws Exception {
        org.mockito.Mockito.when(knowledge.call(anyString(),eq("memory.append"),anyString(),org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new SecurityException("Denied"));
        mvc.perform(post("/api/v1/conversations/"+java.util.UUID.randomUUID()+"/turns")
                .header("Authorization","Bearer "+TestTokens.token("alice","sid-alice"))
                .header("Idempotency-Key",java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(runService);
    }

}
