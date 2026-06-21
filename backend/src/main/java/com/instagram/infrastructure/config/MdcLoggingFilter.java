package com.instagram.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates the SLF4J MDC for every HTTP request so that all log lines
 * produced during that request automatically carry:
 * <ul>
 * <li>{@code requestId} — a random UUID unique to this request</li>
 * <li>{@code userId} — the authenticated user's UUID, or "anonymous"</li>
 * <li>{@code method} — the HTTP method (GET, POST, …)</li>
 * <li>{@code path} — the request URI</li>
 * </ul>
 * MDC keys are cleared in a {@code finally} block to prevent thread-pool
 * leakage.
 */
@Component
@Order(0)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String METHOD_KEY = "method";
    private static final String PATH_KEY = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();

        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(METHOD_KEY, request.getMethod());
        MDC.put(PATH_KEY, request.getRequestURI());
        MDC.put(USER_ID_KEY, resolveUserId());

        // Echo the requestId back to the caller so they can correlate
        response.setHeader("X-Request-Id", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.remove(METHOD_KEY);
            MDC.remove(PATH_KEY);
        }
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return auth.getPrincipal().toString();
    }
}