// Copyright 2026 OLO Labs
// SPDX-License-Identifier: Apache-2.0
package org.olo.app.toolgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.Clock;

/** Wires {@link ToolGateClient} when {@code olo.toolgate.credentials.enabled=true}. */
@Configuration
@EnableConfigurationProperties(ToolGateCredentialProperties.class)
public class ToolGateCredentialConfig {

    @Bean
    @ConditionalOnProperty(prefix = "olo.toolgate.credentials", name = "enabled", havingValue = "true")
    public ToolGateClient toolGateClient(ToolGateCredentialProperties properties, ObjectMapper objectMapper) {
        properties.validate();
        try {
            var key = SessionAssertionSigner.privateKey(Files.readString(properties.privateKeyPath(), StandardCharsets.US_ASCII));
            var signer = new SessionAssertionSigner(key, properties.keyId(), properties.assertionIssuer(),
                    properties.assertionAudience(), properties.assertionLifetime(), objectMapper);
            var http = HttpClient.newBuilder().connectTimeout(properties.requestTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            return new ToolGateClient(properties.controlUrl(), properties.serviceTokenPath(), signer, http,
                    properties.requestTimeout(), objectMapper, Clock.systemUTC());
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("olo.toolgate.credentials.private-key-path must hold a PKCS#8 RSA key", e);
        }
    }
}
