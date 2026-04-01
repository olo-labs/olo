/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.worker.activities.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.worker.activities.OloChatActivities;
import io.temporal.activity.Activity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Activities: report event to Chat BE, planner (TOOL/MODEL/HUMAN/DONE), tool (mock search), model (mock).
 */
public class OloChatActivitiesImpl implements OloChatActivities {

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void reportEvent(String runId, String callbackBaseUrl, long sequenceNumber, String eventType, String correlationId,
                            String nodeId, String parentNodeId, String nodeType, String status,
                            Map<String, Object> input, Map<String, Object> output, Map<String, Object> metadata) {
        String url = callbackBaseUrl.replaceAll("/$", "") + "/api/runs/" + runId + "/events";
        Map<String, Object> body = new HashMap<>();
        body.put("sequenceNumber", sequenceNumber);
        if (eventType != null) body.put("eventType", eventType);
        if (correlationId != null) body.put("correlationId", correlationId);
        body.put("nodeId", nodeId);
        body.put("parentNodeId", parentNodeId);
        body.put("nodeType", nodeType);
        body.put("status", status);
        body.put("input", input != null ? input : Map.of());
        body.put("output", output != null ? output : Map.of());
        body.put("metadata", metadata != null ? metadata : Map.of());

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                System.err.println("reportEvent failed: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.err.println("reportEvent error: " + e.getMessage());
            throw new RuntimeException("Failed to report event to Chat BE", e);
        }
    }

    @Override
    public String planner(String userMessage, String stepsDone) {
        String msg = (userMessage != null ? userMessage : "").toLowerCase();
        String done = stepsDone != null ? stepsDone : "";

        if (msg.contains("approval") || msg.contains("approve")) {
            return "HUMAN";
        }
        if (msg.contains("search") || msg.contains("news")) {
            return "TOOL";
        }
        if (done.contains("TOOL") && !done.contains("MODEL")) {
            return "MODEL";
        }
        if (done.contains("HUMAN") && !done.contains("MODEL")) {
            return "MODEL";
        }
        if (done.contains("MODEL")) {
            return "DONE";
        }
        return "MODEL";
    }

    @Override
    public Map<String, Object> runTool(String toolName, Map<String, Object> params) {
        if ("search_news".equals(toolName)) {
            String query = params != null && params.get("query") != null ? params.get("query").toString() : "";
            String mockNews = "Tesla news: Stock up 2%. New Gigafactory announced. Elon Musk tweets about AI.";
            if (!query.isEmpty()) {
                mockNews = "News about " + query + ": " + mockNews;
            }
            return Map.of("result", mockNews, "tool", "search_news");
        }
        return Map.of("result", "Tool " + toolName + " executed.", "tool", toolName);
    }

    @Override
    public String runModel(String userMessage, String toolResultOrNull) {
        if (toolResultOrNull != null && !toolResultOrNull.isEmpty()) {
            return "Here's what I found:\n\n" + toolResultOrNull + "\n\nLet me know if you need anything else or want to send this for approval.";
        }
        return "I've processed your request. Would you like to send this for approval?";
    }
}
