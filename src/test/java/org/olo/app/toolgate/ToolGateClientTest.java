/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.toolgate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.olo.app.security.store.InMemoryIdentityStore;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3.4: the backend's session assertion and its exchange and revocation calls at ToolGate Control. */
class ToolGateClientTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private final KeyPair keys = rsa();
    private final List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
    private volatile int exchangeStatus = 200;
    private volatile int revokeStatus = 204;
    private volatile String issuedTenant = "t1";
    private volatile long issuedLifetime = 15;
    private HttpServer control;
    private ToolGateClient client;

    @TempDir
    Path temp;

    @BeforeEach
    void start() throws Exception {
        control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        control.createContext("/", exchange -> {
            Map<String, String> call = new HashMap<>();
            call.put("path", exchange.getRequestURI().getPath());
            call.put("authorization", exchange.getRequestHeaders().getFirst("Authorization"));
            call.put("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
            call.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            calls.add(call);
            boolean revoke = call.get("path").endsWith("/sessions/revoke");
            int status = revoke ? revokeStatus : exchangeStatus;
            byte[] body = (status == 200 ? "{\"access_token\":\"" + credential(issuedTenant, issuedLifetime) + "\",\"token_type\":\"Bearer\",\"expires_in\":" + issuedLifetime + "}"
                    : "{\"error\":\"invalid_grant\",\"error_description\":\"no\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        control.start();
        Path token = Files.writeString(temp.resolve("service-token"), "service-jwt\n");
        var signer = new SessionAssertionSigner((RSAPrivateCrtKey) keys.getPrivate(), "olo-1", "https://olo.example/backend",
                "toolgate-token-exchange", Duration.ofSeconds(15), json);
        client = new ToolGateClient(URI.create("http://127.0.0.1:" + control.getAddress().getPort() + "/"), token, signer,
                HttpClient.newHttpClient(), Duration.ofSeconds(5), json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        control.stop(0);
    }

    @Test
    void exchangeSendsASignedAssertionWithTheServiceToken() throws Exception {
        ToolGateClient.Credential credential = client.exchange("t1", "user-42", NOW.plusSeconds(300));
        assertEquals(credential("t1", 15), credential.accessToken());
        assertEquals(15, credential.expiresInSeconds());
        assertTrue(!credential.toString().contains("credential["), "toString never prints the token");

        Map<String, String> call = calls.get(0);
        assertEquals("/api/control/v1/oauth/token-exchange", call.get("path"));
        assertEquals("Bearer service-jwt", call.get("authorization"));
        assertEquals("application/x-www-form-urlencoded", call.get("contentType"));
        Map<String, String> form = new HashMap<>();
        for (String pair : call.get("body").split("&")) {
            String[] kv = pair.split("=", 2);
            form.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        assertEquals(ToolGateClient.GRANT_TYPE, form.get("grant_type"));
        assertEquals(ToolGateClient.JWT_TYPE, form.get("subject_token_type"));

        String[] parts = form.get("subject_token").split("\\.");
        assertEquals("{\"alg\":\"RS256\",\"typ\":\"olo-session-assertion+jwt\",\"kid\":\"olo-1\"}", decode(parts[0]));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keys.getPublic());
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(signature.verify(Base64.getUrlDecoder().decode(parts[2])));
        JsonNode claims = json.readTree(decode(parts[1]));
        assertEquals("https://olo.example/backend", claims.get("iss").asText());
        assertEquals("toolgate-token-exchange", claims.get("aud").asText());
        assertEquals("user-42", claims.get("sub").asText());
        assertEquals(NOW.getEpochSecond(), claims.get("iat").asLong());
        assertEquals("t1", claims.get("tid").asText());
        assertEquals(NOW.getEpochSecond() + 15, claims.get("exp").asLong());
    }

    @Test
    void refusalsCarryToolGatesErrorCode() {
        exchangeStatus = 400;
        var refused = assertThrows(ToolGateClient.ToolGateException.class, () -> client.exchange("t1", "user-42", NOW.plusSeconds(300)));
        assertEquals(400, refused.status());
        assertEquals("invalid_grant", refused.error());
    }

    @Test
    void everyLocalRevocationEndsTheUsersToolGateSessions() throws Exception {
        var local = new InMemoryIdentityStore();
        var store = new RevocationPropagatingIdentityStore(local, client);
        String user = store.userIdFor("https://idp.example", "alice");
        store.session("https://idp.example", "sid-1", user, NOW.plusSeconds(300));

        store.revokeSession("https://idp.example", "sid-1");
        store.revokeAllSessions(user);
        store.removeMembership(user, "t1");
        store.revokeSession("https://idp.example", "never-seen");

        assertEquals(3, calls.size());
        for (Map<String, String> call : calls) {
            assertEquals("/api/control/v1/oauth/sessions/revoke", call.get("path"));
            assertEquals(user, json.readTree(call.get("body")).get("subject").asText());
        }
        assertTrue(local.findSession("https://idp.example", "sid-1").orElseThrow().revoked());
    }

    @Test
    void localRevocationHoldsWhenToolGateIsDown() {
        control.stop(0);
        var local = new InMemoryIdentityStore();
        var store = new RevocationPropagatingIdentityStore(local, client);
        String user = store.userIdFor("https://idp.example", "alice");
        store.session("https://idp.example", "sid-1", user, NOW.plusSeconds(300));
        store.revokeSession("https://idp.example", "sid-1");
        assertTrue(local.findSession("https://idp.example", "sid-1").orElseThrow().revoked());
        assertTrue(local.pendingRevocations().containsKey(user));
    }

    @Test
    void foreignTenantAndExcessiveCredentialLifetimesFailClosed() {
        issuedTenant = "t2";
        assertThrows(ToolGateClient.ToolGateException.class, () -> client.exchange("t1", "user-42", NOW.plusSeconds(300)));
        issuedTenant = "t1";
        issuedLifetime = 60;
        assertThrows(ToolGateClient.ToolGateException.class, () -> client.exchange("t1", "user-42", NOW.plusSeconds(300)));
        issuedLifetime = 15;
        assertThrows(ToolGateClient.ToolGateException.class, () -> client.exchange("t1", "user-42", NOW.plusSeconds(5)));
    }

    @Test
    void failedRevocationsSurviveWorkerRestartAndRetryWithoutAcknowledgingNewerEvents() {
        var local = new InMemoryIdentityStore();
        var store = new RevocationPropagatingIdentityStore(local, client);
        revokeStatus = 503;
        store.revokeAllSessions("user-42");
        assertTrue(local.pendingRevocations().containsKey("user-42"));
        String old = local.pendingRevocations().get("user-42");
        local.enqueueRevocation("user-42", "newer-event");
        local.acknowledgeRevocation("user-42", old);
        assertEquals("newer-event", local.pendingRevocations().get("user-42"));
        revokeStatus = 204;
        new RevocationPropagatingIdentityStore(local, client).retryPendingRevocations();
        assertTrue(local.pendingRevocations().isEmpty());
    }

    private static String credential(String tenant, long lifetime) {
        String claims = "{\"tid\":\"" + tenant + "\",\"exp\":" + (NOW.getEpochSecond() + lifetime) + "}";
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
