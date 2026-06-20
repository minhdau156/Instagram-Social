package com.instagram.infrastructure.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.instagram.infrastructure.interceptor.IdempotencyInterceptor;

import java.io.IOException;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

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
        public void doFilter(ServletRequest request, ServletResponse response,
                FilterChain chain) throws IOException, ServletException {
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(
                    (HttpServletRequest) request);
            wrappedRequest.getInputStream().readAllBytes(); // eagerly populate cache
            chain.doFilter(wrappedRequest, response);
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