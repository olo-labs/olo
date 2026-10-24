/* Copyright (c) 2026 Olo Labs; SPDX-License-Identifier: Apache-2.0 */
package org.olo.app.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.payload.context.WorkflowSerializationContext;
import org.junit.jupiter.api.Test;
import org.olo.app.security.OloSecurityProperties;
import org.olo.app.security.SecurityConfig;
import org.olo.app.security.TestTokens;
import org.olo.input.codec.OloPayloadCodec;
import org.olo.input.codec.PayloadKeyRing;
import org.olo.input.codec.TenantWorkflowIds;
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

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodecServerController.class)
@Import({SecurityConfig.class, CodecServerSecurityTest.Keys.class, org.olo.app.filter.RequestLoggingFilter.class})
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "olo.temporal.codec-server.enabled=true",
        "olo.security.issuer=" + TestTokens.ISSUER, "olo.security.audience=" + TestTokens.AUDIENCE,
        "olo.security.jwk-set-uri=http://localhost:1/jwks", "olo.security.store=memory",
        "olo.security.bootstrap-memberships[0].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[0].subject=root", "olo.security.bootstrap-memberships[0].tenants=t1",
        "olo.security.bootstrap-memberships[1].issuer=" + TestTokens.ISSUER,
        "olo.security.bootstrap-memberships[1].subject=alice", "olo.security.bootstrap-memberships[1].tenants=t1"
})
class CodecServerSecurityTest {
    static final PayloadKeyRing RING = new PayloadKeyRing(Map.of("t1", new byte[32], "t2", new byte[32], "p", new byte[32]),
            Map.of("t1", "t1", "t2", "t2"), "p");
    @TestConfiguration
    static class Keys {
        @Bean @Primary JwtDecoder testJwtDecoder(OloSecurityProperties p) { return TestTokens.decoder(p); }
        @Bean("codecServerKeys") PayloadKeyRing codecServerKeys() { return RING; }
    }
    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private String sealed(String tenant) throws Exception {
        Payload plain = Payload.newBuilder().putMetadata("encoding", ByteString.copyFromUtf8("json/plain"))
                .setData(ByteString.copyFromUtf8("private account")).build();
        Payload sealed = new OloPayloadCodec(RING).withContext(new WorkflowSerializationContext("default", TenantWorkflowIds.run(tenant, "r")))
                .encode(List.of(plain)).get(0);
        Map<String, String> metadata = new LinkedHashMap<>();
        sealed.getMetadataMap().forEach((k, v) -> metadata.put(k, Base64.getEncoder().encodeToString(v.toByteArray())));
        return json.writeValueAsString(Map.of("payloads", List.of(Map.of("metadata", metadata,
                "data", Base64.getEncoder().encodeToString(sealed.getData().toByteArray())))));
    }

    @Test
    void onlyAnAuthenticatedTenantAdministratorMayDecode() throws Exception {
        String body = sealed("t1");
        mvc.perform(post("/api/codec/decode").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/codec/decode").header("Authorization", "Bearer " + TestTokens.token("alice", "user-sid"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/codec/decode").header("Authorization", "Bearer " + TestTokens.adminToken("root", "admin-sid"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.payloads[0].data").value(Base64.getEncoder().encodeToString("private account".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void evenAnAdministratorCannotDecryptAnotherTenantsPayload() throws Exception {
        mvc.perform(post("/api/codec/decode").header("Authorization", "Bearer " + TestTokens.adminToken("root", "admin-sid"))
                .contentType(MediaType.APPLICATION_JSON).content(sealed("t2"))).andExpect(status().isForbidden());
    }

    @Test
    void encodeUsesTheAuthenticatedTenantAndDoesNotLogPlaintextPayloads(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String secret = Base64.getEncoder().encodeToString("private account".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String request = json.writeValueAsString(Map.of("payloads", List.of(Map.of("metadata", Map.of(
                "encoding", Base64.getEncoder().encodeToString("json/plain".getBytes(java.nio.charset.StandardCharsets.UTF_8))), "data", secret))));
        mvc.perform(post("/api/codec/encode").header("Authorization", "Bearer " + TestTokens.adminToken("root", "admin-sid"))
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.payloads[0].metadata.olo-tenant").value("dDE="));
        org.assertj.core.api.Assertions.assertThat(output.getAll()).doesNotContain(secret, "private account");
    }
}
