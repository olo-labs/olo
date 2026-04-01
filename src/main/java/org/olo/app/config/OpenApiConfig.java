/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */

package org.olo.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:7080}")
    private int serverPort;

    @Bean
    public OpenAPI oloOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Olo Chat API")
                        .description("REST API for Olo chat: sessions, messages, runs, execution events (SSE), and human approval.")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("Olo")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local")
                ));
    }
}
