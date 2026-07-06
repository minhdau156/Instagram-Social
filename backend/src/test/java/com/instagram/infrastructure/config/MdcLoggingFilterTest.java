package com.instagram.infrastructure.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void doFilterInternal_setsXRequestIdResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader("X-Request-Id")).isNotNull().isNotBlank();
    }

    @Test
    void doFilterInternal_callsFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_clearsMdcAfterExecution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/feed");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("path")).isNull();
    }

    @Test
    void doFilterInternal_setsAnonymousUserIdWhenNotAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture MDC during filter execution
        final String[] capturedUserId = new String[1];
        FilterChain capturingChain = (req, res) -> capturedUserId[0] = MDC.get("userId");

        filter.doFilterInternal(request, response, capturingChain);

        assertThat(capturedUserId[0]).isEqualTo("anonymous");
    }

    @Test
    void doFilterInternal_setsAuthenticatedUserId() throws Exception {
        String username = "user-uuid-123";
        User userDetails = new User(username, "", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedUserId = new String[1];
        FilterChain capturingChain = (req, res) -> capturedUserId[0] = MDC.get("userId");

        filter.doFilterInternal(request, response, capturingChain);

        assertThat(capturedUserId[0]).isEqualTo(username);
    }

    @Test
    void doFilterInternal_setsMethodAndPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/posts/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] capturedMethod = new String[1];
        final String[] capturedPath = new String[1];
        FilterChain capturingChain = (req, res) -> {
            capturedMethod[0] = MDC.get("method");
            capturedPath[0] = MDC.get("path");
        };

        filter.doFilterInternal(request, response, capturingChain);

        assertThat(capturedMethod[0]).isEqualTo("DELETE");
        assertThat(capturedPath[0]).isEqualTo("/api/v1/posts/123");
    }

    @Test
    void doFilterInternal_xRequestIdIsUuidFormat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        String requestId = response.getHeader("X-Request-Id");
        // UUID format: 8-4-4-4-12 hex chars
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
