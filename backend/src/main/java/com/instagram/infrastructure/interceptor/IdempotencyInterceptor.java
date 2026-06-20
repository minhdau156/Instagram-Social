package com.instagram.infrastructure.interceptor;

import com.instagram.adapter.out.persistence.entity.IdempotencyKeyJpaEntity;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Enforces idempotency for POST endpoints that declare "X-Idempotent: true"
 * (set as a request attribute by the controller, or matched by path pattern).
 *
 * Flow:
 * preHandle — if key exists and hashes match, write stored response and halt.
 * if key exists and hashes differ, write 409 and halt.
 * afterCompletion — if key is new and response is 2xx, store key + response.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyKeyJpaRepository repository;

    public IdempotencyInterceptor(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String rawKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            return true; // no key supplied — pass through normally
        }

        UUID key;
        try {
            key = UUID.fromString(rawKey);
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"data\":null,\"error\":\"Idempotency-Key must be a valid UUID\"}");
            return false;
        }

        UUID userId = currentUserId();
        if (userId == null) {
            return true; // unauthenticated — let security filter handle it
        }

        String requestHash = hashRequestBody(request);
        Optional<IdempotencyKeyJpaEntity> stored = repository.findByKeyAndUserId(key, userId);

        if (stored.isPresent()) {
            IdempotencyKeyJpaEntity record = stored.get();
            if (!record.getRequestHash().equals(requestHash)) {
                // Same key, different body — this is a conflict
                response.setStatus(409);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"data\":null,\"error\":\"Idempotency key was already used with a different request body\"}");
                log.warn("Idempotency key {} reused with different payload by user {}", key, userId);
                return false;
            }
            // Same key, same body — return the stored response
            response.setStatus(record.getHttpStatus());
            response.setContentType("application/json");
            response.getWriter().write(record.getResponseBody());
            log.debug("Idempotency key {} already processed — returning cached response", key);
            return false;
        }

        // First time seeing this key — store it in request scope for afterCompletion
        request.setAttribute("idempotencyKey", key);
        request.setAttribute("idempotencyUserId", userId);
        request.setAttribute("idempotencyHash", requestHash);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        UUID key = (UUID) request.getAttribute("idempotencyKey");
        UUID userId = (UUID) request.getAttribute("idempotencyUserId");
        String hash = (String) request.getAttribute("idempotencyHash");

        if (key == null || userId == null) {
            return; // no idempotency key was set for this request
        }

        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return; // only cache successful responses
        }

        // Read the response body (requires ContentCachingResponseWrapper — see
        // WebMvcConfig)
        String responseBody = "";
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            responseBody = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        IdempotencyKeyJpaEntity record = new IdempotencyKeyJpaEntity();
        record.setKey(key);
        record.setUserId(userId);
        record.setEndpoint(request.getRequestURI());
        record.setRequestHash(hash);
        record.setResponseBody(responseBody);
        record.setHttpStatus(status);
        repository.save(record);

        log.debug("Stored idempotency key {} for user {} endpoint {}", key, userId, request.getRequestURI());
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String hashRequestBody(HttpServletRequest request) {
        try {
            // ✅ Read from cache wrapper instead of original stream
            byte[] body = getRequestBodyBytes(request);

            if (body.length == 0) {
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(body));
        } catch (Exception e) {
            log.warn("Could not hash request body for idempotency check: {}", e.getMessage());
            return "";
        }
    }

    private byte[] getRequestBodyBytes(HttpServletRequest request) {
        // Your filter wraps the request, so we can safely cast
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            return wrapper.getContentAsByteArray();
        }

        // Fallback - but this will fail if called multiple times
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("Could not read request body", e);
            return new byte[0];
        }
    }
}