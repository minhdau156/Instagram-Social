package com.instagram.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JpaConfigTest {

    private final JpaConfig jpaConfig = new JpaConfig();

    @Test
    void dateTimeProvider_returnsNonEmptyOptional() {
        DateTimeProvider provider = jpaConfig.dateTimeProvider();
        Optional<TemporalAccessor> result = provider.getNow();
        assertThat(result).isPresent();
    }

    @Test
    void dateTimeProvider_returnsOffsetDateTime() {
        DateTimeProvider provider = jpaConfig.dateTimeProvider();
        Optional<TemporalAccessor> result = provider.getNow();
        assertThat(result.get()).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void dateTimeProvider_returnsCurrentTime() {
        DateTimeProvider provider = jpaConfig.dateTimeProvider();
        OffsetDateTime before = OffsetDateTime.now();
        OffsetDateTime result = (OffsetDateTime) provider.getNow().get();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(result).isBetween(before, after);
    }

    @Test
    void dateTimeProvider_eachCallReturnsNewInstant() throws InterruptedException {
        DateTimeProvider provider = jpaConfig.dateTimeProvider();
        OffsetDateTime first = (OffsetDateTime) provider.getNow().get();
        Thread.sleep(2);
        OffsetDateTime second = (OffsetDateTime) provider.getNow().get();

        assertThat(second).isAfterOrEqualTo(first);
    }
}
