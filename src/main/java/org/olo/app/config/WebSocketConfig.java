/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import org.olo.app.ws.RunEventWebSocketHandler;
import org.olo.app.ws.WebSocketAuthHandshakeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * WebSocket endpoint at /ws. Client sends Authorization: Bearer JWT; backend validates and stores tenantId in session.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RunEventWebSocketHandler runEventWebSocketHandler;
    private final WebSocketAuthHandshakeHandler handshakeHandler;

    public WebSocketConfig(RunEventWebSocketHandler runEventWebSocketHandler,
                           WebSocketAuthHandshakeHandler handshakeHandler) {
        this.runEventWebSocketHandler = runEventWebSocketHandler;
        this.handshakeHandler = handshakeHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(runEventWebSocketHandler, "/ws")
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*");
    }
}
