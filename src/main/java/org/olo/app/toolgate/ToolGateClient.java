// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls ToolGate Control as the OLO backend: RFC 8693 token exchange of a session assertion for a ToolGate user
 * credential, and session revocation. Authenticates with the backend's own Control service JWT.
 */
public class ToolGateClient {

    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    static final String JWT_TYPE = "urn:ietf:params:oauth:token-type:jwt";

    private final URI exchange;
    private final URI revoke;
    private final Path serviceTokenPath;
    private final SessionAssertionSigner signer;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper json;
    private final Clock clock;

    public ToolGateClient(URI controlUrl, Path serviceTokenPath, SessionAssertionSigner signer, HttpClient http,
                          Duration timeout, ObjectMapper json, Clock clock) {
        String base = controlUrl.toString().replaceAll("/+$", "");
        this.exchange = URI.create(base + "/api/control/v1/oauth/token-exchange");
        this.revoke = URI.create(base + "/api/control/v1/oauth/sessions/revoke");
        this.serviceTokenPath = serviceTokenPath;
        this.signer = signer;
        this.http = http;
        this.timeout = timeout;
        this.json = json;
        this.clock = clock;
    }

    /** A ToolGate user credential. Never logged or stored. */
    public record Credential(String accessToken, long expiresInSeconds) {
        @Override
        public String toString() {
            return "Credential[expiresInSeconds=" + expiresInSeconds + "]";
        }
    }

    /** Thrown when ToolGate refuses or cannot be reached; carries ToolGate's error code when it sent one. */
    public static final class ToolGateException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;
        private final String error;

        ToolGateException(int status, String error, String message) {
            super(message);
            this.status = status;
            this.error = error;
        }

        public int status() {
            return status;
        }

        public String error() {
            return error;
        }
    }

    /** Exchanges a fresh assertion for {@code userId}, the stable OLO user id. */
    public Credential exchange(String tenantId, String userId, java.time.Instant authenticatedUntil) {
        java.time.Instant now = clock.instant();
        String form = Map.of(
                        "grant_type", GRANT_TYPE,
                        "subject_token_type", JWT_TYPE,
                        "requested_token_type", JWT_TYPE,
                        "subject_token", signer.sign(tenantId, userId, now, authenticatedUntil))
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpResponse<String> response = send(HttpRequest.newBuilder(exchange)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
        JsonNode body = parse(response);
        if (response.statusCode() != 200 || !body.path("access_token").isTextual() || !body.path("expires_in").canConvertToLong()
                || body.path("expires_in").asLong() <= 0) {
            throw refusal(response, body);
        }
        String token = body.get("access_token").asText();
        // The exchange is reached over authenticated TLS. Refuse a token for the configured Control tenant when it
        // differs from the requested run tenant; never return that foreign credential to a worker.
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("malformed credential");
            JsonNode claims = json.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
            long expiry = claims.path("exp").asLong(0);
            long maximum = Math.min(now.plusSeconds(15).getEpochSecond(), authenticatedUntil.getEpochSecond());
            if (!tenantId.equals(claims.path("tid").asText()) || expiry <= clock.instant().getEpochSecond() || expiry > maximum) {
                throw new IllegalArgumentException("credential tenant or lifetime mismatch");
            }
            return new Credential(token, Math.min(body.get("expires_in").asLong(), expiry - clock.instant().getEpochSecond()));
        } catch (IOException | IllegalArgumentException e) {
            throw new ToolGateException(502, "invalid_credential_binding", "ToolGate returned a credential outside the run's tenant or lifetime");
        }
    }

    /** Ends every ToolGate session of {@code userId}: outstanding credentials stop at Control's next authorization. */
    public void revoke(String userId) {
        String document;
        try {
            document = json.writeValueAsString(Map.of("subject", userId));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(revoke)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(document)));
        if (response.statusCode() != 204) {
            throw refusal(response, parse(response));
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            String token = Files.readString(serviceTokenPath, StandardCharsets.US_ASCII).strip();
            return http.send(request.timeout(timeout).header("Authorization", "Bearer " + token).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ToolGateException(503, "unavailable", "ToolGate Control is unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolGateException(503, "unavailable", "interrupted");
        }
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            JsonNode node = json.readTree(response.body() == null || response.body().isEmpty() ? "{}" : response.body());
            return node == null ? json.createObjectNode() : node;
        } catch (IOException e) {
            return json.createObjectNode();
        }
    }

    private static ToolGateException refusal(HttpResponse<String> response, JsonNode body) {
        String error = body.path("error").isTextual() ? body.get("error").asText() : "refused";
        return new ToolGateException(response.statusCode(), error, "ToolGate refused: " + error);
    }
}
