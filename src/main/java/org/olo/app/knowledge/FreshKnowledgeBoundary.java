// Copyright 2026 OLO Labs; SPDX-License-Identifier: Apache-2.0
package org.olo.app.knowledge;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * D8: old tenant/file/demo APIs are closed, so they cannot bypass the governed services during M9
 * cleanup.
 */
@Component
public final class FreshKnowledgeBoundary extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean old =
                path.startsWith("/api/sessions")
                        || path.matches("/api/tenants/[^/]+/sessions.*")
                        || path.startsWith("/api/resource/")
                        || path.startsWith("/api/rag/")
                        || path.startsWith("/api/knowledge/")
                        || path.equals("/api/documents")
                        || path.equals("/ws")
                        || path.startsWith("/ws/")
                        || (path.startsWith("/api/runs/")
                                && !(request.getMethod().equals("POST")
                                        && path.matches("/api/runs/[^/]+/events")));
        if (old) {
            response.setStatus(410);
            response.setContentType("application/json");
            response.getWriter()
                    .write(
                            "{\"error\":\"Fresh installation requires the governed /api/v1"
                                    + " contract\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
