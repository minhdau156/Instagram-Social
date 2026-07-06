package com.instagram.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_returnsNonNull() {
        CorsConfig config = new CorsConfig();
        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isNotNull();
    }

    @Test
    void corsConfigurationSource_allowsCredentials() {
        CorsConfig config = new CorsConfig();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/posts");

        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowCredentials()).isTrue();
    }

    @Test
    void corsConfigurationSource_allowsExpectedMethods() {
        CorsConfig config = new CorsConfig();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/any-path");

        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }

    @Test
    void corsConfigurationSource_allowsExpectedHeaders() {
        CorsConfig config = new CorsConfig();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/any-path");

        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedHeaders())
                .containsExactlyInAnyOrder("Authorization", "Content-Type");
    }

    @Test
    void corsConfigurationSource_appliesGlobalPattern() {
        CorsConfig config = new CorsConfig();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/totally/different/path");

        CorsConfiguration corsConfig = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(corsConfig).isNotNull();
    }
}
