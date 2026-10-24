// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.knowledge;

import com.fasterxml.jackson.databind.JsonNode;

import org.olo.app.security.CurrentPrincipal;
import org.olo.app.service.RunService;
import org.olo.app.store.ChatRunStore;
import org.olo.app.workflow.impl.WorkflowInputSerializer;
import org.olo.input.model.IdentityBinding;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fresh-install API. The caller selects opaque resources; verified identity and permissions always
 * come from ToolGate.
 */
@RestController
@RequestMapping("/api/v1")
public final class KnowledgeController {
    @GetMapping("/conversations/{id}/receipts/{invocation}")
    public JsonNode receipt(@PathVariable UUID id, @PathVariable String invocation) {
        return knowledge.read(id.toString(), Map.of("type", "receipt", "invocationId", invocation));
    }

    private final PlatformKnowledge knowledge;
    private final RunService runs;
    private final ChatRunStore runStore;
    private final String queue, callback, pipeline;

    public KnowledgeController(
            PlatformKnowledge knowledge,
            RunService runs,
            ChatRunStore runStore,
            @Qualifier("oloTaskQueue") String queue,
            @Qualifier("oloCallbackBaseUrl") String callback,
            @Value("${olo.chat.pipeline:governed-chat}") String pipeline) {
        this.knowledge = knowledge;
        this.runs = runs;
        this.runStore = runStore;
        this.queue = queue;
        this.callback = callback;
        this.pipeline = pipeline;
    }

    public record Turn(String message, String kbId) {}

    @PostMapping("/conversations/{id}")
    public JsonNode create(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
        return knowledge.call(
                key + ":create",
                "memory.append",
                id.toString(),
                Map.of("type", "create", "create", true));
    }

    @PostMapping("/conversations/{id}/turns")
    public ResponseEntity<?> turn(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Turn turn) {
        if (turn.message() == null || turn.message().isBlank() || turn.message().length() > 131072)
            throw new IllegalArgumentException("Message required");
        if (turn.kbId() != null) UUID.fromString(turn.kbId());
        var p = CurrentPrincipal.require();
        String run =
                UUID.nameUUIDFromBytes(
                                (p.tenantId() + ":" + p.userId() + ":" + id + ":" + key)
                                        .getBytes(StandardCharsets.UTF_8))
                        .toString();
        String message =
                UUID.nameUUIDFromBytes((run + ":user").getBytes(StandardCharsets.UTF_8)).toString();
        // Accept and persist before scheduling; the root lifecycle repeats this exact idempotent
        // append.
        knowledge.call(
                key + ":accept",
                "memory.append",
                id.toString(),
                Map.of(
                        "messageId",
                        message,
                        "turnId",
                        run,
                        "role",
                        "user",
                        "content",
                        turn.message(),
                        "dependencies",
                        List.of(),
                        "create",
                        true));
        runStore.put(
                new ChatRunStore.RunRecord(
                        run, id.toString(), message, p.tenantId(), run, null, null, null));
        var input =
                WorkflowInputSerializer.build(
                        p.tenantId(),
                        id.toString(),
                        message,
                        turn.message(),
                        pipeline,
                        run,
                        run,
                        callback,
                        run,
                        turn.kbId(),
                        new IdentityBinding(
                                p.tenantId(),
                                p.userId(),
                                p.issuer(),
                                p.sessionId(),
                                p.sessionEpoch()));
        try {
            runs.startWorkflow(run, input, queue);
        } catch (io.temporal.client.WorkflowExecutionAlreadyStarted alreadyStarted) {
            /* Same immutable turn was accepted previously. */
        }
        return ResponseEntity.accepted().body(Map.of("runId", run, "messageId", message));
    }

    @GetMapping("/conversations/{id}/messages")
    public JsonNode history(@PathVariable UUID id) {
        return knowledge.read(id.toString(), Map.of("budget", 4000));
    }

    @GetMapping("/conversations/{id}/turns/{turn}")
    public JsonNode result(@PathVariable UUID id, @PathVariable UUID turn) {
        return knowledge.read(id.toString(), Map.of("type", "turn", "turnId", turn.toString()));
    }

    @PostMapping("/conversations/{id}/turns/{turn}/cancel")
    public Map<String, Object> cancel(
            @PathVariable UUID id,
            @PathVariable UUID turn,
            @RequestHeader("Idempotency-Key") UUID key) {
        knowledge.call(
                key + ":cancel-authorize", "memory.append", id.toString(), Map.of("type", "check"));
        var run = runStore.get(turn.toString());
        if (run == null) {
            // Rebuild only from governed durable history after a backend restart.
            var history =
                    knowledge.read(
                            id.toString(), Map.of("type", "turn", "turnId", turn.toString()));
            String acceptedMessage = null;
            for (var item : history.path("messages"))
                if ("user".equals(item.path("role").asText()))
                    acceptedMessage = item.path("message_id").asText();
            if (acceptedMessage == null || acceptedMessage.isBlank())
                throw new SecurityException("Run unavailable");
            run =
                    new ChatRunStore.RunRecord(
                            turn.toString(),
                            id.toString(),
                            acceptedMessage,
                            CurrentPrincipal.require().tenantId(),
                            turn.toString(),
                            null,
                            null,
                            null);
            runStore.put(run);
        }
        if (run == null
                || !id.toString().equals(run.sessionId)
                || !CurrentPrincipal.require().tenantId().equals(run.tenantId))
            throw new SecurityException("Run unavailable");
        runs.cancelRun(turn.toString());
        return Map.of("cancellationRequested", true);
    }

    @DeleteMapping("/conversations/{id}")
    public JsonNode delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
        return knowledge.call(key + ":delete", "memory.delete", id.toString(), Map.of());
    }

    @PostMapping("/conversations/{id}/share")
    public JsonNode share(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Map<String, Object> body) {
        return knowledge.call(key + ":share", "memory.share", id.toString(), body);
    }

    @PostMapping("/conversations/{id}/export")
    public JsonNode export(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key) {
        knowledge.read(id.toString(), Map.of("budget", 4000));
        return knowledge.call(key + ":export", "memory.export", id.toString(), Map.of());
    }

    @PostMapping("/knowledge/{kb}/{operation}")
    public JsonNode document(
            @PathVariable UUID kb,
            @PathVariable String operation,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody Map<String, Object> body) {
        if (!Set.of("query", "ingest", "update", "delete", "share").contains(operation))
            throw new IllegalArgumentException("Unsupported document operation");
        return knowledge.call(
                operation.equals("query") ? UUID.randomUUID().toString() : key + ":" + operation,
                "rag." + operation,
                kb.toString(),
                body);
    }
}
