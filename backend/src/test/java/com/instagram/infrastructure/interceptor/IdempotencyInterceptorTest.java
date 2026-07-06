package com.instagram.infrastructure.interceptor;

import com.instagram.adapter.out.persistence.entity.IdempotencyKeyJpaEntity;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    @Mock
    private IdempotencyKeyJpaRepository repository;

    private IdempotencyInterceptor interceptor;

    private UUID userId;

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyInterceptor(repository);
        userId = UUID.randomUUID();
        authenticateAs(userId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID id) {
        User userDetails = new User(id.toString(), "", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletRequest requestWithKey(UUID key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        if (key != null) {
            request.addHeader("Idempotency-Key", key.toString());
        }
        if (body != null) {
            request.setContent(body.getBytes());
        }
        return request;
    }

    // ── no idempotency key ────────────────────────────────────────────────────

    @Test
    void preHandle_returnsTrueWhenNoKeyHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void preHandle_returnsTrueWhenKeyHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Idempotency-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    // ── invalid key format ────────────────────────────────────────────────────

    @Test
    void preHandle_returns400WhenKeyIsNotUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.addHeader("Idempotency-Key", "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(400);
    }

    // ── first-time key (not seen before) ──────────────────────────────────────

    @Test
    void preHandle_returnsTrueWhenKeyNotYetStored() throws Exception {
        UUID key = UUID.randomUUID();
        when(repository.findByKeyAndUserId(eq(key), eq(userId))).thenReturn(Optional.empty());

        MockHttpServletRequest request = requestWithKey(key, "{\"caption\":\"hello\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void preHandle_storesKeyAttributesInRequestWhenNewKey() throws Exception {
        UUID key = UUID.randomUUID();
        when(repository.findByKeyAndUserId(eq(key), eq(userId))).thenReturn(Optional.empty());

        MockHttpServletRequest request = requestWithKey(key, "{\"caption\":\"hello\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(request.getAttribute("idempotencyKey")).isEqualTo(key);
        assertThat(request.getAttribute("idempotencyUserId")).isEqualTo(userId);
    }

    // ── duplicate key, same body ──────────────────────────────────────────────

    @Test
    void preHandle_returnsFalseAndReplaysCachedResponseWhenSameKeyAndBody() throws Exception {
        UUID key = UUID.randomUUID();
        String body = "{\"caption\":\"hello\"}";

        // Pre-compute the hash the way the interceptor does
        byte[] bodyBytes = body.getBytes();
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String expectedHash = java.util.Base64.getEncoder().encodeToString(digest.digest(bodyBytes));

        IdempotencyKeyJpaEntity stored = new IdempotencyKeyJpaEntity();
        stored.setKey(key);
        stored.setUserId(userId);
        stored.setRequestHash(expectedHash);
        stored.setResponseBody("{\"data\":{\"id\":\"123\"}}");
        stored.setHttpStatus(201);

        when(repository.findByKeyAndUserId(eq(key), eq(userId))).thenReturn(Optional.of(stored));

        MockHttpServletRequest request = requestWithKey(key, body);
        request.setAttribute("_cachedRequestBody", bodyBytes);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"data\":{\"id\":\"123\"}}");
    }

    // ── duplicate key, different body ─────────────────────────────────────────

    @Test
    void preHandle_returns409WhenSameKeyButDifferentBody() throws Exception {
        UUID key = UUID.randomUUID();

        IdempotencyKeyJpaEntity stored = new IdempotencyKeyJpaEntity();
        stored.setKey(key);
        stored.setUserId(userId);
        stored.setRequestHash("original-hash");
        stored.setResponseBody("{}");
        stored.setHttpStatus(201);

        when(repository.findByKeyAndUserId(eq(key), eq(userId))).thenReturn(Optional.of(stored));

        MockHttpServletRequest request = requestWithKey(key, "{\"caption\":\"different body\"}");
        byte[] differentBody = "{\"caption\":\"different body\"}".getBytes();
        request.setAttribute("_cachedRequestBody", differentBody);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
    }

    // ── unauthenticated ───────────────────────────────────────────────────────

    @Test
    void preHandle_returnsTrueWhenNotAuthenticated() throws Exception {
        SecurityContextHolder.clearContext();
        UUID key = UUID.randomUUID();

        MockHttpServletRequest request = requestWithKey(key, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(repository);
    }

    // ── afterCompletion: store on success ─────────────────────────────────────

    @Test
    void afterCompletion_storesRecordWhenSuccessfulResponse() throws Exception {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.setAttribute("idempotencyKey", key);
        request.setAttribute("idempotencyUserId", userId);
        request.setAttribute("idempotencyHash", "abc123hash");

        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper response = new ContentCachingResponseWrapper(rawResponse);
        response.setStatus(201);
        response.getWriter().write("{\"data\":{\"id\":\"new\"}}");
        response.flushBuffer();

        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<IdempotencyKeyJpaEntity> captor =
                ArgumentCaptor.forClass(IdempotencyKeyJpaEntity.class);
        verify(repository).save(captor.capture());

        IdempotencyKeyJpaEntity saved = captor.getValue();
        assertThat(saved.getKey()).isEqualTo(key);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEndpoint()).isEqualTo("/api/v1/posts");
        assertThat(saved.getRequestHash()).isEqualTo("abc123hash");
        assertThat(saved.getHttpStatus()).isEqualTo(201);
    }

    @Test
    void afterCompletion_doesNotStoreWhenNoKeyAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(repository);
    }

    @Test
    void afterCompletion_doesNotStoreWhenResponseIsNot2xx() throws Exception {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.setAttribute("idempotencyKey", key);
        request.setAttribute("idempotencyUserId", userId);
        request.setAttribute("idempotencyHash", "hash");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(repository);
    }

    @Test
    void afterCompletion_doesNotStoreWhenResponse4xx() throws Exception {
        UUID key = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.setAttribute("idempotencyKey", key);
        request.setAttribute("idempotencyUserId", userId);
        request.setAttribute("idempotencyHash", "hash");

        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(400);

        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(repository);
    }
}
