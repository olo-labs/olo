/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.olo.app.security.callback.WorkerCallbackAuthenticationFilter;
import org.olo.app.security.callback.WorkerCallbackProperties;
import org.olo.app.security.store.IdentityStore;
import org.olo.app.security.store.InMemoryIdentityStore;
import org.olo.app.security.store.RedisIdentityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every OLO API call is authenticated with a verified JWT (M3.1). There is no unauthenticated or demo mode.
 *
 * <p>Requests that skip token authentication, and why:
 * <ul>
 *   <li>{@code olo.security.public-paths}: health and API docs</li>
 *   <li>{@code /ws}: the WebSocket handshake authenticates the token itself ({@code WebSocketAuthHandshakeHandler})</li>
 *   <li>{@code POST /api/runs/{runId}/events}: the worker's run-event callback. It is service-to-service, so it is
 *       authenticated by an HMAC signature ({@link WorkerCallbackAuthenticationFilter}) instead of a user token.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties({OloSecurityProperties.class, WorkerCallbackProperties.class})
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public IdentityStore identityStore(OloSecurityProperties properties, ObjectProvider<StringRedisTemplate> redis,
                                       ObjectProvider<org.olo.app.toolgate.ToolGateClient> toolGate) {
        properties.validate();
        IdentityStore store;
        if ("memory".equals(properties.store())) {
            log.warn("olo.security.store=memory: identity, membership and session state is per process and lost on restart. Tests only.");
            store = new InMemoryIdentityStore();
        } else {
            store = new RedisIdentityStore(redis.getObject());
        }
        // With ToolGate user credentials on, every revocation also ends the user's ToolGate sessions.
        var client = toolGate.getIfAvailable();
        return client == null ? store : new org.olo.app.toolgate.RevocationPropagatingIdentityStore(store, client);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(OloSecurityProperties properties) {
        return OloJwtDecoders.fromProperties(properties);
    }

    @Bean
    public OloPrincipalResolver oloPrincipalResolver(IdentityStore identityStore, OloSecurityProperties properties) {
        return new OloPrincipalResolver(identityStore, properties);
    }

    @Bean
    public SecurityFilterChain oloSecurityFilterChain(HttpSecurity http,
                                                      OloSecurityProperties properties,
                                                      JwtDecoder jwtDecoder,
                                                      OloPrincipalResolver resolver,
                                                      WorkerCallbackProperties callbackProperties,
                                                      ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> writeError(response, objectMapper, ex);
        AccessDeniedHandler accessDenied = (request, response, ex) ->
                writeError(response, objectMapper, HttpStatus.FORBIDDEN, "forbidden", "access denied");

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(auth -> {
                    for (String path : properties.publicPaths()) {
                        auth.requestMatchers(path).permitAll();
                    }
                    auth.requestMatchers("/ws").permitAll();
                    auth.requestMatchers(WorkerCallbackAuthenticationFilter.CALLBACK)
                            .hasAuthority(WorkerCallbackAuthenticationFilter.ROLE_WORKER);
                    auth.requestMatchers("/api/admin/**").hasAuthority(OloAuthenticationToken.ROLE_ADMIN);
                    auth.requestMatchers("/api/codec/**").hasAuthority(OloAuthenticationToken.ROLE_ADMIN);
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(new OloBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new OloJwtAuthenticationConverter(resolver, properties.tenantHeader()))))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
                // Not a bean: a Filter bean would also be registered with the servlet container and run twice.
                .addFilterBefore(new WorkerCallbackAuthenticationFilter(callbackProperties, Clock.systemUTC()),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Applies {@code olo.security.bootstrap-memberships}, so a fresh installation has its first members and admins. */
    @Bean
    public ApplicationRunner oloBootstrapMemberships(OloSecurityProperties properties, IdentityStore identityStore) {
        return args -> {
            for (OloSecurityProperties.BootstrapMembership membership : properties.bootstrapMemberships()) {
                String userId = identityStore.userIdFor(membership.issuer(), membership.subject());
                for (String tenant : membership.tenants()) {
                    identityStore.addMembership(userId, tenant);
                }
                log.info("Bootstrap membership: user {} in tenants {}", userId, membership.tenants());
            }
        };
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper, AuthenticationException ex)
            throws IOException {
        if (ex instanceof OloIdentityException identity) {
            writeError(response, objectMapper, identity.status(), identity.code(), identity.getMessage());
        } else {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeError(response, objectMapper, HttpStatus.UNAUTHORIZED, "invalid_token", "a valid bearer token is required");
        }
    }

    private static void writeError(HttpServletResponse response, ObjectMapper objectMapper,
                                   HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
