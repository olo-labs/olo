// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.knowledge;

import com.fasterxml.jackson.databind.*;

import org.olo.app.security.CurrentPrincipal;
import org.olo.app.toolgate.ToolGateCredentialController;
import org.olo.toolgate.client.*;
import org.olo.toolgate.client.impl.McpToolGateClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.*;

/**
 * Backend uses the same user-bound Gateway path as the worker. No agent token or service fallback
 * exists.
 */
@Service
public final class PlatformKnowledge {
    private final GovernedCalls calls;

    public PlatformKnowledge(
            ToolGateCredentialController exchange,
            @Value("${olo.toolgate.gateway-url:https://localhost:7443/mcp}") String gateway) {
        var settings =
                new ToolGateSettings(URI.create(gateway), null, null, Duration.ofSeconds(20));
        var client =
                new McpToolGateClient(
                        settings,
                        name -> {
                            throw new SecurityException("An end-user credential is required");
                        },
                        binding -> {
                            var user = CurrentPrincipal.require();
                            if (!user.tenantId().equals(binding.tenantId())
                                    || !user.userId().equals(binding.userId())
                                    || !user.issuer().equals(binding.issuer())
                                    || !user.sessionId().equals(binding.sessionId())
                                    || user.sessionEpoch() != binding.sessionEpoch())
                                throw new SecurityException("Identity binding mismatch");
                            var response =
                                    exchange.credential(
                                            new ToolGateCredentialController.BindingRef(
                                                    binding.tenantId(),
                                                    binding.userId(),
                                                    binding.issuer(),
                                                    binding.sessionId(),
                                                    binding.sessionEpoch()));
                            if (!response.getStatusCode().is2xxSuccessful()
                                    || response.getBody() == null)
                                throw new SecurityException("User authority unavailable");
                            return (String) response.getBody().get("accessToken");
                        });
        calls = new GovernedCalls(client);
    }

    public JsonNode call(
            String id, String operation, String resource, Map<String, Object> request) {
        var p = CurrentPrincipal.require();
        var result =
                calls.user(
                        new UserBinding(
                                p.tenantId(),
                                p.userId(),
                                p.issuer(),
                                p.sessionId(),
                                p.sessionEpoch()),
                        p.userId() + ":" + id,
                        operation,
                        resource,
                        request);
        if (operation.equals("memory.export")) validate(result);
        return result;
    }

    public JsonNode read(String conversation, Map<String, Object> request) {
        var result = call(UUID.randomUUID().toString(), "memory.read", conversation, request);
        // Historical/derived responses are not released just because the conversation is still
        // visible.
        validate(result);
        return result;
    }

    private void validate(JsonNode result) {
        long began = System.nanoTime();
        if (result.has("authorizedAt")) fresh(result);
        for (var message : result.path("messages"))
            for (var dep : message.path("dependencies")) {
                if (!dep.has("kbId")) continue;
                var check =
                        call(
                                UUID.randomUUID().toString(),
                                "rag.query",
                                dep.path("kbId").asText(),
                                Map.of(
                                        "type",
                                        "check",
                                        "sources",
                                        List.of(
                                                Map.of(
                                                        "docId",
                                                        dep.path("docId").asText(),
                                                        "version",
                                                        dep.path("version").asLong()))));
                fresh(check);
                if (check.path("valid").size() != 1
                        || check.path("aclVersion").asLong() != dep.path("aclVersion").asLong())
                    throw new SecurityException(
                            "A source was revoked; the derived response cannot be released");
            }
        if (System.nanoTime() - began > java.util.concurrent.TimeUnit.SECONDS.toNanos(10))
            throw new SecurityException("Source validation deadline exceeded");
    }

    private static void fresh(JsonNode result) {
        long at = result.path("authorizedAt").asLong(0), now = System.currentTimeMillis();
        if (at > now + 2000 || now - at > 10000)
            throw new SecurityException("Source authorization proof expired");
    }
}
