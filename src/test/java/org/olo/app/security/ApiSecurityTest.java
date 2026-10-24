/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import org.junit.jupiter.api.Test;
import org.olo.app.controller.AuthController;
import org.olo.app.controller.IdentityAdminController;
import org.olo.app.controller.TenantSessionsController;
import org.olo.app.controller.TenantsController;
import org.olo.app.store.ChatMessageStore;
import org.olo.app.store.ChatSessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real security filter chain, end to end over HTTP, with tokens signed by a test key.
 */
@WebMvcTest(controllers = {AuthController.class, TenantsController.class, TenantSessionsController.class,
        IdentityAdminController.class})
@Import({SecurityConfig.class, ApiSecurityTest.TestKeys.class, ChatSessionStore.class, ChatMessageStore.class})
@TestPropertySource(properties = {
        "olo.security.issuer=" + TestTokens.ISSUER,
        "olo.security.audience=" + TestTokens.AUDIENCE,
        "olo.security.jwk-set-uri=http://localhost:1/jwks",
        "olo.security.store=memory",
        "olo.security.bootstrap-memberships[0].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[0].subject=alice",
        "olo.security.bootstrap-memberships[0].tenants=t1",
        "olo.security.bootstrap-memberships[1].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[1].subject=bob",
        "olo.security.bootstrap-memberships[1].tenants=t1,t2",
        "olo.security.bootstrap-memberships[2].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[2].subject=root",
        "olo.security.bootstrap-memberships[2].tenants=t1"
})
class ApiSecurityTest {

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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void requestsWithoutAValidTokenAreRefused() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_token"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.tokenSignedByOtherKey("alice", "s1"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization",
                        bearer(TestTokens.token("alice", "s1", b -> b.expirationTime(Date.from(Instant.now().minusSeconds(300)))))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization",
                        bearer(TestTokens.token("alice", "s1", b -> b.audience(List.of("other"))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifiedMemberGetsTheirIdentity() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.token("alice", "a1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t1"))
                .andExpect(jsonPath("$.sessionEpoch").value(1))
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void nonMemberIsForbidden() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.token("stranger", "x1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("no_membership"));
    }

    @Test
    void memberOfSeveralTenantsPicksOneWithTheHeader() throws Exception {
        String token = bearer(TestTokens.token("bob", "b1"));

        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("tenant_required"));
        mvc.perform(get("/api/auth/me").header("Authorization", token).header("X-OLO-Tenant", "t2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("t2"));
        mvc.perform(get("/api/auth/me").header("Authorization", token).header("X-OLO-Tenant", "t9"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("not_a_member"));
    }

    @Test
    void legacyTenantSessionPathsAreClosedOnAFreshInstall() throws Exception {
        String token = bearer(TestTokens.token("alice", "a2"));

        mvc.perform(get("/api/tenants/t1/sessions").header("Authorization", token)).andExpect(status().isGone());
        mvc.perform(get("/api/tenants/t2/sessions").header("Authorization", token)).andExpect(status().isGone());
        mvc.perform(delete("/api/tenants/t2/sessions").header("Authorization", token)).andExpect(status().isGone());
    }

    @Test
    void tenantsListShowsOnlyTheCallersMemberships() throws Exception {
        mvc.perform(get("/api/tenants").header("Authorization", bearer(TestTokens.token("bob", "b2"))).header("X-OLO-Tenant", "t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[1].id").value("t2"));
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        String token = bearer(TestTokens.token("alice", "a3"));
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", token)).andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("session_revoked"));
        // A new sign-in (new sid) works.
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.token("alice", "a4"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointsNeedTheAdminRole() throws Exception {
        String body = "{\"issuer\":\"" + TestTokens.ISSUER + "\",\"subject\":\"carol\"}";

        mvc.perform(post("/api/admin/tenants/t1/members").header("Authorization", bearer(TestTokens.token("alice", "a5")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.token("carol", "c1"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/tenants/t1/members").header("Authorization", bearer(TestTokens.adminToken("root", "r1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("t1"));
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(TestTokens.token("carol", "c2"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantId").value("t1"));
    }

    @Test
    void adminCanRevokeAUsersSessions() throws Exception {
        String dave = bearer(TestTokens.token("dave", "d1"));
        String member = "{\"issuer\":\"" + TestTokens.ISSUER + "\",\"subject\":\"dave\"}";
        mvc.perform(post("/api/admin/tenants/t1/members").header("Authorization", bearer(TestTokens.adminToken("root", "r2")))
                .contentType(MediaType.APPLICATION_JSON).content(member)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", dave)).andExpect(status().isOk());

        mvc.perform(post("/api/admin/auth/users/revoke-sessions").header("Authorization", bearer(TestTokens.adminToken("root", "r2")))
                        .contentType(MediaType.APPLICATION_JSON).content(member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(1));

        mvc.perform(get("/api/auth/me").header("Authorization", dave)).andExpect(status().isUnauthorized());
    }
}
