package com.instagram.infrastructure.config;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.spi.CachingProvider;
import javax.cache.expiry.Duration;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@EnableCaching
@Configuration
public class RateLimitConfig {

    @Bean
    public javax.cache.CacheManager bucket4jJCacheManager() {
        // Get the default CachingProvider (will use Caffeine if available)
        CachingProvider provider = Caching.getCachingProvider();
        javax.cache.CacheManager cacheManager = provider.getCacheManager();

        // Configure the cache for rate limiting buckets
        MutableConfiguration<String, byte[]> configuration = new MutableConfiguration<>();
        configuration.setTypes(String.class, byte[].class);
        configuration.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.MINUTES, 10)));
        configuration.setStoreByValue(false); // More efficient for byte arrays

        // Create the cache that matches your filter's cache-name
        cacheManager.createCache("rate-limit-buckets", configuration);

        return cacheManager;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> retryAfterFilter() {
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                filterChain.doFilter(request, response);
                if (response.getStatus() == 429) {
                    response.setHeader("Retry-After", "60");
                }
            }
        });
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Integer.MIN_VALUE + 1);
        return bean;
    }

}
