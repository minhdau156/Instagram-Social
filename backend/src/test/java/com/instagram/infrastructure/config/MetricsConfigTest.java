package com.instagram.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    private MetricsConfig metricsConfig;
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        metricsConfig = new MetricsConfig();
        registry = new SimpleMeterRegistry();
    }

    @Test
    void postsCreatedCounter_isRegistered() {
        Counter counter = metricsConfig.postsCreatedCounter(registry);
        assertThat(counter).isNotNull();
    }

    @Test
    void postsCreatedCounter_hasCorrectName() {
        metricsConfig.postsCreatedCounter(registry);
        assertThat(registry.find("posts.published").counter()).isNotNull();
    }

    @Test
    void postsCreatedCounter_hasFeatureTag() {
        metricsConfig.postsCreatedCounter(registry);
        Counter found = registry.find("posts.published").tag("feature", "posts").counter();
        assertThat(found).isNotNull();
    }

    @Test
    void postsCreatedCounter_initialCountIsZero() {
        Counter counter = metricsConfig.postsCreatedCounter(registry);
        assertThat(counter.count()).isEqualTo(0.0);
    }

    @Test
    void postsCreatedCounter_incrementsCorrectly() {
        Counter counter = metricsConfig.postsCreatedCounter(registry);
        counter.increment();
        counter.increment();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void likesAddedCounter_isRegistered() {
        Counter counter = metricsConfig.likesAddedCounter(registry);
        assertThat(counter).isNotNull();
    }

    @Test
    void likesAddedCounter_hasCorrectName() {
        metricsConfig.likesAddedCounter(registry);
        assertThat(registry.find("likes.added").counter()).isNotNull();
    }

    @Test
    void likesAddedCounter_hasFeatureTag() {
        metricsConfig.likesAddedCounter(registry);
        Counter found = registry.find("likes.added").tag("feature", "interactions").counter();
        assertThat(found).isNotNull();
    }

    @Test
    void likesAddedCounter_incrementsCorrectly() {
        Counter counter = metricsConfig.likesAddedCounter(registry);
        counter.increment(3);
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void usersRegisteredCounter_isRegistered() {
        Counter counter = metricsConfig.usersRegisteredCounter(registry);
        assertThat(counter).isNotNull();
    }

    @Test
    void usersRegisteredCounter_hasCorrectName() {
        metricsConfig.usersRegisteredCounter(registry);
        assertThat(registry.find("users.registered").counter()).isNotNull();
    }

    @Test
    void usersRegisteredCounter_hasFeatureTag() {
        metricsConfig.usersRegisteredCounter(registry);
        Counter found = registry.find("users.registered").tag("feature", "auth").counter();
        assertThat(found).isNotNull();
    }

    @Test
    void usersRegisteredCounter_incrementsCorrectly() {
        Counter counter = metricsConfig.usersRegisteredCounter(registry);
        counter.increment();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
