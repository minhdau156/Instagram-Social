package com.instagram.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers named Micrometer counters as Spring beans.
 *
 * Naming convention: noun_verb (snake_case), e.g. posts_created_total.
 * Prometheus will suffix _total automatically for Counter types.
 */
@Configuration
public class MetricsConfig {

    /**
     * Incremented each time a post is successfully persisted.
     * Exposed as: posts_created_total{application="instagram"}
     */
    @Bean
    public Counter postsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("posts.published")
                .description("Number of posts successfully published")
                .tag("feature", "posts")
                .register(registry);
    }

    /**
     * Incremented each time a like is toggled on (not off).
     * Exposed as: likes_added_total{application="instagram"}
     */
    @Bean
    public Counter likesAddedCounter(MeterRegistry registry) {
        return Counter.builder("likes.added")
                .description("Number of likes added (not removed)")
                .tag("feature", "interactions")
                .register(registry);
    }

    /**
     * Incremented each time a new user successfully registers.
     * Exposed as: users_registered_total{application="instagram"}
     */
    @Bean
    public Counter usersRegisteredCounter(MeterRegistry registry) {
        return Counter.builder("users.registered")
                .description("Number of new user registrations")
                .tag("feature", "auth")
                .register(registry);
    }
}