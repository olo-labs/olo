/*
 * Copyright (c) 2026 Olo Labs
 * SPDX-License-Identifier: Apache-2.0
 */
package org.olo.app.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Reads the bearer token from the {@code Authorization} header. Only for the run event stream (SSE), where browsers
 * can't set headers, it also accepts the {@code access_token} query parameter on GET.
 *
 * <p>Query tokens can end up in proxy logs, so they're allowed only on that one read-only stream, and tokens are
 * short-lived.
 */
public final class OloBearerTokenResolver implements BearerTokenResolver {

    private static final RequestMatcher EVENT_STREAM = new AntPathRequestMatcher("/api/runs/*/events", HttpMethod.GET.name());

    private final DefaultBearerTokenResolver headerOnly = new DefaultBearerTokenResolver();
    private final DefaultBearerTokenResolver headerOrQuery = new DefaultBearerTokenResolver();

    public OloBearerTokenResolver() {
        headerOrQuery.setAllowUriQueryParameter(true);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return EVENT_STREAM.matches(request) ? headerOrQuery.resolve(request) : headerOnly.resolve(request);
    }
}
