/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.domain;

import java.util.Map;

/**
 * Core execution model. Emit this for: workflow start, planner decision,
 * model call, tool call, human wait/completion, retry, failure.
 * Schema is versioned for evolution; event log is consumed by replay/diff in Admin BE or tooling, not by Chat BE.
 */
public class OloExecutionEvent {

    /** Schema version of this event (e.g. 1). Increment when fields or semantics change. */
    private int eventVersion = 1;

    private String runId;
    private String nodeId;
    private String parentNodeId;

    private NodeType nodeType;
    private NodeStatus status;

    /** Explicit type for analytics; cleaner than deriving from nodeType + status. */
    private EventType eventType;

    private long timestamp;

    /** Required for ordering and idempotency: UNIQUE(runId, sequenceNumber). */
    private Long sequenceNumber;

    /** Cross-service tracing. Set at run creation and propagated to every event. */
    private String correlationId;

    private Map<String, Object> input;
    private Map<String, Object> output;
    private Map<String, Object> metadata;

    public OloExecutionEvent() {}

    public int getEventVersion() { return eventVersion; }
    public void setEventVersion(int eventVersion) { this.eventVersion = eventVersion; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getParentNodeId() { return parentNodeId; }
    public void setParentNodeId(String parentNodeId) { this.parentNodeId = parentNodeId; }

    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }

    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }

    public Map<String, Object> getOutput() { return output; }
    public void setOutput(Map<String, Object> output) { this.output = output; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
