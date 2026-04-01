/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.controller;

import org.olo.app.service.ChatRedisPersistence;
import org.olo.app.store.ChatMessageStore;
import org.olo.app.store.ChatSessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * List and delete chat sessions for a tenant. Used by the Conversation UI to show all sessions,
 * default to the latest, and "Delete all" for that pipeline/tenant.
 */
@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenant sessions", description = "List and delete chat sessions per tenant for Conversation")
public class TenantSessionsController {

    @Autowired(required = false)
    private ChatSessionStore sessionStore;

    @Autowired(required = false)
    private ChatMessageStore messageStore;

    @Autowired(required = false)
    private ChatRedisPersistence redisPersistence;

    @Operation(summary = "List sessions", description = "Returns chat sessions for the tenant, optionally filtered by queue and pipeline (per workflow queue and pipeline view). Most recently active first.")
    @GetMapping("/{tenantId}/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions(
            @PathVariable String tenantId,
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String pipeline) {
        List<ChatSessionStore.SessionRecord> records = null;
        if (redisPersistence != null) {
            records = redisPersistence.listSessionsByTenant(tenantId, queue, pipeline);
        }
        if (records == null && sessionStore != null) {
            records = sessionStore.listByTenant(tenantId, queue, pipeline);
        }
        if (records == null) {
            return ResponseEntity.ok(List.of());
        }
        if (sessionStore != null) {
            for (ChatSessionStore.SessionRecord r : records) {
                sessionStore.put(r);
            }
        }
        List<Map<String, Object>> list = records.stream()
                .map(s -> Map.<String, Object>of(
                        "sessionId", s.sessionId,
                        "tenantId", s.tenantId,
                        "createdAt", s.createdAt,
                        "lastActivityAt", s.lastActivityAt
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Delete all sessions", description = "Deletes chat sessions and their messages for the tenant, optionally limited to queue and pipeline (current view).")
    @DeleteMapping("/{tenantId}/sessions")
    public ResponseEntity<Void> deleteAllSessions(
            @PathVariable String tenantId,
            @RequestParam(required = false) String queue,
            @RequestParam(required = false) String pipeline) {
        if (sessionStore != null) {
            List<String> removed = sessionStore.deleteAllForTenant(tenantId, queue, pipeline);
            if (messageStore != null && !removed.isEmpty()) {
                messageStore.removeBySessionIds(removed);
            }
        }
        if (redisPersistence != null) {
            redisPersistence.deleteAllForTenant(tenantId, queue, pipeline);
        }
        return ResponseEntity.noContent().build();
    }
}
