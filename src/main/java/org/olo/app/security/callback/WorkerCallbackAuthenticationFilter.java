/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security.callback;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authenticates the worker's run-event callback ({@code POST /api/runs/{runId}/events}) by its HMAC signature
 * ({@link CallbackSignature}). On success the request runs as {@link #ROLE_WORKER}; on any failure it is refused with
 * 401 before reaching the controller.
 *
 * <p>Replays are bounded by the timestamp window, and the controller rejects duplicate sequence numbers.
 */
public final class WorkerCallbackAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE_WORKER = "ROLE_OLO_WORKER";
    /** Worker-signed endpoints: run-event callbacks, and ToolGate user credentials for a run's user (M3.4). */
    public static final RequestMatcher CALLBACK = new org.springframework.security.web.util.matcher.OrRequestMatcher(
            new AntPathRequestMatcher("/api/runs/*/events", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/internal/toolgate/credentials", HttpMethod.POST.name()));

    private static final Logger log = LoggerFactory.getLogger(WorkerCallbackAuthenticationFilter.class);

    private final Map<String, byte[]> keys;
    private final long maxSkewSeconds;
    private final int maxBodyBytes;
    private final Clock clock;

    public WorkerCallbackAuthenticationFilter(WorkerCallbackProperties properties, Clock clock) {
        this.keys = Map.copyOf(properties.keyMap());
        this.maxSkewSeconds = properties.maxSkew().toSeconds();
        this.maxBodyBytes = properties.maxBodyBytes();
        this.clock = Objects.requireNonNull(clock, "clock");
        if (keys.isEmpty()) {
            log.warn("No olo.worker-callback keys configured: every worker run-event callback will be refused");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CALLBACK.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String keyId = request.getHeader(CallbackSignature.HEADER_KEY);
        byte[] key = keyId == null ? null : keys.get(keyId);
        if (key == null) {
            refuse(response, "unknown_key", "unknown or missing callback key");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(String.valueOf(request.getHeader(CallbackSignature.HEADER_TIMESTAMP)).trim());
        } catch (NumberFormatException e) {
            refuse(response, "bad_timestamp", "missing or invalid callback timestamp");
            return;
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > maxSkewSeconds) {
            refuse(response, "stale", "callback timestamp outside the accepted window");
            return;
        }
        byte[] body = readBody(request);
        if (body == null) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        String expected = CallbackSignature.sign(key, timestamp, request.getMethod(), request.getRequestURI(), body);
        if (!CallbackSignature.matches(expected, request.getHeader(CallbackSignature.HEADER_SIGNATURE))) {
            refuse(response, "bad_signature", "callback signature does not match");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new WorkerAuthentication(keyId));
        try {
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes(maxBodyBytes + 1);
            return body.length > maxBodyBytes ? null : body;
        }
    }

    private static void refuse(HttpServletResponse response, String code, String message) throws IOException {
        log.info("Worker callback refused: {}", code);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** An authenticated worker, identified by the key it signed with. */
    static final class WorkerAuthentication extends AbstractAuthenticationToken {
        private final String keyId;

        WorkerAuthentication(String keyId) {
            super(List.of(new SimpleGrantedAuthority(ROLE_WORKER)));
            this.keyId = keyId;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return "worker:" + keyId;
        }
    }

    /** Replays the already-read, verified body to the controller. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
