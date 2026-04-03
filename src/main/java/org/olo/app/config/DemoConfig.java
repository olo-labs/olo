/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.olo.app.store.*;
import org.olo.app.auth.JwtTenantIdDecoder;
import org.olo.app.ws.DefaultJwtTenantExtractor;
import org.olo.app.ws.JwtTenantExtractor;
import org.olo.app.ws.RunEventWebSocketHandler;
import org.olo.app.ws.RunEventWebSocketRegistry;
import org.olo.app.ws.WebSocketAuthHandshakeHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.olo.sdk.TemporalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class DemoConfig {

    @Value("${olo.temporal.target:localhost:7233}")
    private String temporalTarget;

    @Value("${olo.temporal.namespace:default}")
    private String temporalNamespace;

    @Value("${olo.chat.callback-base-url:http://localhost:7080}")
    private String callbackBaseUrl;

    @Value("${olo.temporal.task-queue:olo-chat}")
    private String taskQueue;

    @Value("${olo.temporal.workflow-type:OloChatWorkflowImpl}")
    private String workflowTypeDefault;

    @Bean
    public ExecutionEventStore executionEventStore() {
        return new ExecutionEventStore();
    }

    @Bean
    public RunEventWebSocketRegistry runEventWebSocketRegistry() {
        return new RunEventWebSocketRegistry();
    }

    @Bean
    public RunEventBroadcaster runEventBroadcaster(ExecutionEventStore executionEventStore,
                                                   RunEventWebSocketRegistry runEventWebSocketRegistry,
                                                   ObjectMapper objectMapper) {
        return new RunEventBroadcaster(executionEventStore, runEventWebSocketRegistry, objectMapper);
    }

    @Bean
    public RunEventWebSocketHandler runEventWebSocketHandler(ExecutionEventStore executionEventStore,
                                                             RunEventWebSocketRegistry runEventWebSocketRegistry,
                                                             ChatRunStore chatRunStore,
                                                             ObjectMapper objectMapper) {
        return new RunEventWebSocketHandler(executionEventStore, runEventWebSocketRegistry, chatRunStore, objectMapper);
    }

    @Bean
    public JwtTenantExtractor jwtTenantExtractor(JwtTenantIdDecoder jwtTenantIdDecoder) {
        return new DefaultJwtTenantExtractor(jwtTenantIdDecoder);
    }

    @Bean
    public WebSocketAuthHandshakeHandler webSocketAuthHandshakeHandler(JwtTenantExtractor jwtTenantExtractor,
                                                                       @Value("${olo.ws.jwt.required:true}") boolean wsJwtRequired,
                                                                       @Value("${olo.default-tenant-id:default}") String defaultTenantId) {
        return new WebSocketAuthHandshakeHandler(new DefaultHandshakeHandler(), jwtTenantExtractor, wsJwtRequired, defaultTenantId);
    }

    @Bean
    public ChatSessionStore chatSessionStore() {
        return new ChatSessionStore();
    }

    @Bean
    public ChatMessageStore chatMessageStore() {
        return new ChatMessageStore();
    }

    @Bean
    public ChatRunStore chatRunStore() {
        return new ChatRunStore();
    }

    @Bean
    public TemporalClient temporalClient() {
        String workflowType = System.getenv("OLO_WORKFLOW_TYPE");
        if (workflowType == null || workflowType.isEmpty()) {
            workflowType = workflowTypeDefault;
        }
        return TemporalClient.newBuilder()
                .target(temporalTarget)
                .namespace(temporalNamespace)
                .workflowType(workflowType)
                .build();
    }

    @Bean(name = "oloCallbackBaseUrl")
    public String callbackBaseUrl() {
        return callbackBaseUrl;
    }

    @Bean(name = "oloTaskQueue")
    public String taskQueue() {
        return taskQueue;
    }

    /** Executor for awaiting Temporal workflow completion (non-blocking). */
    @Bean(name = "workflowCompletionExecutor")
    public Executor workflowCompletionExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "workflow-completion");
            t.setDaemon(true);
            return t;
        });
    }
}
