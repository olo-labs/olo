/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.api.request.AppendEventRequest;
import org.olo.app.api.request.HumanInputRequest;
import org.olo.app.service.RunService;
import org.olo.app.store.ChatRunStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/runs")
@Tag(name = "Runs", description = "Chat runs, execution events (SSE), and human approval")
public class RunsController {

    private static final Logger log = LoggerFactory.getLogger(RunsController.class);

    private final RunService runService;
    private final ChatRunStore runStore;
    private final ObjectMapper objectMapper;

    public RunsController(RunService runService, ChatRunStore runStore, ObjectMapper objectMapper) {
        this.runService = runService;
        this.runStore = runStore;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Operation(summary = "Stream run events (SSE)", description = "Server-sent events stream: catch-up then live execution events (PLANNER, TOOL, MODEL, HUMAN)")
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String runId) {
        log.info("[BE SSE] 1. streamEvents: client connected runId={}", runId);
        SseEmitter emitter = new SseEmitter(0L);
        runService.getBroadcaster().subscribeWithCatchUp(runId, emitter);
        return emitter;
    }

    @Operation(summary = "Append event (worker)", description = "Worker callback: append one execution event (PLANNER, TOOL, MODEL, HUMAN approval, etc.). Stored and forwarded to UI via SSE. sequenceNumber required; duplicate returns 409.")
    @PostMapping("/{runId}/events")
    public ResponseEntity<Void> appendEvent(@PathVariable String runId, @RequestBody AppendEventRequest body) {
        try {
            String bodyJson = this.objectMapper.writeValueAsString(body);
            log.info("[BE SSE] 2. appendEvent: complete message received runId={} body={}", runId, bodyJson);
        } catch (JsonProcessingException e) {
            log.info("[BE SSE] 2. appendEvent: POST received runId={} nodeType={} status={} hasOutput={} seq={}",
                    runId,
                    body.getNodeType() != null ? body.getNodeType().name() : null,
                    body.getStatus() != null ? body.getStatus().name() : null,
                    body.getOutput() != null && !body.getOutput().isEmpty(),
                    body.getSequenceNumber());
        }
        if (body.getSequenceNumber() == null) {
            return ResponseEntity.badRequest().build();
        }
        String correlationId = body.getCorrelationId();
        if (correlationId == null) {
            ChatRunStore.RunRecord run = runStore.get(runId);
            if (run != null) correlationId = run.correlationId;
        }
        try {
            runService.appendEvent(
                    runId,
                    body.getNodeId(),
                    body.getParentNodeId(),
                    body.getNodeType() != null ? body.getNodeType().name() : "SYSTEM",
                    body.getStatus() != null ? body.getStatus().name() : "STARTED",
                    body.getInput(),
                    body.getOutput(),
                    body.getMetadata(),
                    body.getSequenceNumber(),
                    body.getEventVersion(),
                    body.getEventType(),
                    correlationId
            );
            log.info("[BE SSE] 3. appendEvent: event stored and broadcast for runId={}", runId);
            return ResponseEntity.noContent().build();
        } catch (org.olo.app.store.DuplicateSequenceException e) {
            log.warn("[BE SSE] 3b. appendEvent: duplicate sequence runId={} seq={}", runId, body.getSequenceNumber());
            return ResponseEntity.status(409).build();
        }
    }

    @Operation(summary = "Human input", description = "User approval or text for a HUMAN step; signals the workflow and resumes execution")
    @PostMapping("/{runId}/human-input")
    public ResponseEntity<Void> humanInput(@PathVariable String runId, @RequestBody(required = false) HumanInputRequest body) {
        boolean approved = body != null && body.isApproved();
        String message = body != null ? body.getMessage() : null;
        runService.signalHumanInput(runId, approved, message);
        runStore.setStatus(runId, "running");
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cancel run", description = "Cancels an in-progress workflow execution")
    @PostMapping("/{runId}/cancel")
    public ResponseEntity<Void> cancelRun(@PathVariable String runId) {
        try {
            runService.cancelRun(runId);
            return ResponseEntity.noContent().build();
        } catch (org.olo.app.service.impl.run.RunCanceller.RunNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (org.olo.app.service.impl.run.RunCanceller.RunNotCancellableException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @Operation(summary = "Get run", description = "Get run status and metadata")
    @GetMapping("/{runId}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable String runId) {
        ChatRunStore.RunRecord run = runStore.get(runId);
        if (run == null) return ResponseEntity.notFound().build();
        Map<String, Object> map = new java.util.HashMap<>(Map.of(
                "runId", run.runId,
                "sessionId", run.sessionId,
                "messageId", run.messageId,
                "status", run.status,
                "createdAt", run.createdAt
        ));
        if (run.correlationId != null) map.put("correlationId", run.correlationId);
        if (run.workflowVersion != null) map.put("workflowVersion", run.workflowVersion);
        if (run.modelVersion != null) map.put("modelVersion", run.modelVersion);
        if (run.plannerVersion != null) map.put("plannerVersion", run.plannerVersion);
        return ResponseEntity.ok(map);
    }

    @Operation(summary = "Get run response", description = "Current assistant response for this run from event store (last MODEL or SYSTEM COMPLETED with output). Query when receiving events or while run in progress.")
    @GetMapping("/{runId}/response")
    public ResponseEntity<Map<String, Object>> getRunResponse(@PathVariable String runId) {
        if (runStore.get(runId) == null) return ResponseEntity.notFound().build();
        String response = runService.getRunResponse(runId);
        return ResponseEntity.ok(Map.of("runId", runId, "response", response != null ? response : ""));
    }
}
