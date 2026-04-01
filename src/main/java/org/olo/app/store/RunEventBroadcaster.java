/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.api.ExecutionEventSanitizer;
import org.olo.app.domain.OloExecutionEvent;
import org.olo.app.ws.RunEventWebSocketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Broadcasts execution events to SSE and WebSocket subscribers for a run.
 * Events come from two sources: (1) worker POST /api/runs/{runId}/events (human approval, MODEL output, steps),
 * (2) backend when Temporal workflow completes (SYSTEM COMPLETED/FAILED). All are forwarded to the UI.
 * Events are sanitized before send (product-level fields only).
 */
public class RunEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RunEventBroadcaster.class);

    private final ExecutionEventStore eventStore;
    private final RunEventWebSocketRegistry wsRegistry;
    private final ObjectMapper objectMapper;

    /** runId -> list of active SSE emitters */
    private final java.util.Map<String, List<SseEmitter>> emittersByRunId = new java.util.concurrent.ConcurrentHashMap<>();

    private static final long HEARTBEAT_INTERVAL_SEC = 25L;

    public RunEventBroadcaster(ExecutionEventStore eventStore) {
        this(eventStore, null, null);
    }

    public RunEventBroadcaster(ExecutionEventStore eventStore,
                               RunEventWebSocketRegistry wsRegistry,
                               ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.wsRegistry = wsRegistry;
        this.objectMapper = objectMapper;
        startHeartbeat();
    }

    /** Send periodic comment to all SSE connections so proxies/browsers don't close them. */
    private void startHeartbeat() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        for (Map.Entry<String, List<SseEmitter>> entry : emittersByRunId.entrySet()) {
            String runId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    removeEmitter(runId, emitter);
                }
            }
        }
    }

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByRunId.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        return emitter;
    }

    /** Send all existing events for runId to the given emitter (catch-up), then keep emitter for live. */
    public void subscribeWithCatchUp(String runId, SseEmitter emitter) {
        List<OloExecutionEvent> catchUp = eventStore.getEvents(runId);
        int sent = 0;
        for (OloExecutionEvent event : catchUp) {
            OloExecutionEvent sanitized = ExecutionEventSanitizer.forApi(event);
            if (objectMapper != null) {
                try {
                    String eventJson = objectMapper.writeValueAsString(sanitized);
                    log.info("[BE SSE] subscribeWithCatchUp: event forwarded to socket runId={} payload={}", runId, eventJson);
                } catch (JsonProcessingException e) {
                    log.debug("[BE SSE] subscribeWithCatchUp: could not serialize event for log runId={}", runId, e);
                }
            }
            try {
                emitter.send(SseEmitter.event().data(sanitized));
                sent++;
            } catch (IOException e) {
                log.warn("[BE SSE] subscribeWithCatchUp: send failed runId={} after {} events", runId, sent, e);
                return;
            }
        }
        log.info("[BE SSE] subscribeWithCatchUp: runId={} sent {} catch-up events, emitter registered", runId, sent);
        emittersByRunId.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
    }

    public void broadcast(String runId, OloExecutionEvent event) {
        String nodeType = event.getNodeType() != null ? event.getNodeType().name() : null;
        String status = event.getStatus() != null ? event.getStatus().name() : null;
        boolean hasOutput = event.getOutput() != null && !event.getOutput().isEmpty();
        List<SseEmitter> emitters = emittersByRunId.get(runId);
        int count = emitters != null ? emitters.size() : 0;
        log.info("[BE SSE] broadcast: runId={} nodeType={} status={} hasOutput={} subscribers={}", runId, nodeType, status, hasOutput, count);
        OloExecutionEvent sanitized = ExecutionEventSanitizer.forApi(event);
        if (objectMapper != null) {
            try {
                String eventJson = objectMapper.writeValueAsString(sanitized);
                log.info("[BE SSE] broadcast: event forwarded to socket runId={} payload={}", runId, eventJson);
            } catch (JsonProcessingException e) {
                log.debug("[BE SSE] broadcast: could not serialize event for log runId={}", runId, e);
            }
        }
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().data(sanitized));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(runId, emitter);
                }
            }
        }
        if (wsRegistry != null && objectMapper != null) {
            try {
                String envelope = objectMapper.writeValueAsString(Map.of("type", "RUN_EVENT", "payload", sanitized));
                wsRegistry.sendToRun(runId, envelope);
            } catch (JsonProcessingException e) {
                // log and skip WS push
            }
        }
    }

    private void removeEmitter(String runId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByRunId.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emittersByRunId.remove(runId);
        }
    }
}
