package com.instagram.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    void interestExecutor_isNotNull() {
        Executor executor = asyncConfig.interestExecutor();
        assertThat(executor).isNotNull();
    }

    @Test
    void mediaExecutor_isNotNull() {
        Executor executor = asyncConfig.mediaExecutor();
        assertThat(executor).isNotNull();
    }

    @Test
    void interestExecutor_hasCorrectThreadNamePrefix() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.interestExecutor();
        executor.getThreadGroup(); // force initialization check
        assertThat(executor.getThreadNamePrefix()).isEqualTo("interest-");
    }

    @Test
    void mediaExecutor_hasCorrectThreadNamePrefix() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.mediaExecutor();
        assertThat(executor.getThreadNamePrefix()).isEqualTo("media-worker-");
    }

    @Test
    void interestExecutor_hasCorePoolSize2() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.interestExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
    }

    @Test
    void interestExecutor_hasMaxPoolSize4() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.interestExecutor();
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    }

    @Test
    void mediaExecutor_hasCorePoolSize2() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.mediaExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
    }

    @Test
    void mediaExecutor_hasMaxPoolSize4() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.mediaExecutor();
        assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    }

    @Test
    void interestExecutor_executesTask() throws InterruptedException {
        Executor executor = asyncConfig.interestExecutor();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(latch::countDown);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void mediaExecutor_executesTask() throws InterruptedException {
        Executor executor = asyncConfig.mediaExecutor();
        CountDownLatch latch = new CountDownLatch(1);

        executor.execute(latch::countDown);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }
}
