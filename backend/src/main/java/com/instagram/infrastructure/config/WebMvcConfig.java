package com.instagram.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.instagram.infrastructure.interceptor.IdempotencyInterceptor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String CACHED_BODY_ATTR = "_cachedRequestBody";

    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/v1/posts", "/api/v1/messages");
    }

    /**
     * ✅ REQUEST CACHING FILTER
     * Wraps HttpServletRequest to cache the body for multiple reads
     * Uses ContentCachingRequestWrapper (which EXISTS in Spring)
     */
    @Bean
    public FilterRegistrationBean<Filter> requestCachingFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RequestCachingFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /**
     * ✅ RESPONSE CACHING FILTER
     * Wraps HttpServletResponse to cache the body for afterCompletion
     * Uses ContentCachingResponseWrapper (which EXISTS in Spring)
     */
    @Bean
    public FilterRegistrationBean<Filter> responseCachingFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ResponseCachingFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(2);
        return bean;
    }

    /**
     * Inner class for request caching filter
     */
    private static class RequestCachingFilter implements Filter {
        @Override
        public void doFilter(ServletRequest req, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            byte[] body = request.getInputStream().readAllBytes();
            request.setAttribute(WebMvcConfig.CACHED_BODY_ATTR, body);
            chain.doFilter(new ReReadableRequest(request, body), response);
        }

        private static class ReReadableRequest extends HttpServletRequestWrapper {
            private final byte[] body;

            ReReadableRequest(HttpServletRequest request, byte[] body) {
                super(request);
                this.body = body;
            }

            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream bais = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return bais.read(); }
                    @Override public boolean isFinished() { return bais.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener l) {}
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream()));
            }
        }
    }

    /**
     * Inner class for response caching filter
     */
    private static class ResponseCachingFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {
            if (response instanceof HttpServletResponse) {
                ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(
                        (HttpServletResponse) response);
                chain.doFilter(request, wrappedResponse);
                wrappedResponse.copyBodyToResponse();
            } else {
                chain.doFilter(request, response);
            }
        }
    }
}