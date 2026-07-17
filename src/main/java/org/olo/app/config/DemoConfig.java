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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.olo.temporal.sdk.TemporalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class DemoConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoConfig.class);

    @Value("${olo.temporal.target:localhost:7233}")
    private String temporalTarget;

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
                                                                       ResolvedOloRuntimeSettings runtimeSettings) {
        return new WebSocketAuthHandshakeHandler(new DefaultHandshakeHandler(), jwtTenantExtractor, wsJwtRequired, runtimeSettings.tenantId());
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
    public KnowledgeSourceStore knowledgeSourceStore() {
        return new KnowledgeSourceStore();
    }

    @Bean
    public TemporalClient temporalClient(ResolvedOloRuntimeSettings runtimeSettings) {
        return TemporalClient.newBuilder()
                .target(temporalTarget)
                .namespace(runtimeSettings.temporalNamespace())
                .workflowType(runtimeSettings.workflowType())
                .build();
    }

    @Bean(name = "oloCallbackBaseUrl")
    public String callbackBaseUrl(@Value("${olo.chat.callback-base-url:http://localhost:7080}") String callbackBaseUrl) {
        log.info("Olo worker callback base URL: {}", callbackBaseUrl);
        return callbackBaseUrl;
    }

    @Bean(name = "oloTaskQueue")
    public String taskQueue(ResolvedOloRuntimeSettings runtimeSettings) {
        return runtimeSettings.taskQueue();
    }

    @Bean(name = "oloDefaultTenantId")
    public String defaultTenantId(ResolvedOloRuntimeSettings runtimeSettings) {
        return runtimeSettings.tenantId();
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
