package com.instagram.infrastructure.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.repository.HashtagStatsJpaRepository;

@Component
public class HashtagStatsScheduler {

    private static final Logger log = LoggerFactory.getLogger(HashtagStatsScheduler.class);

    private final HashtagStatsJpaRepository hashtagStatsJpaRepository;

    public HashtagStatsScheduler(HashtagStatsJpaRepository hashtagStatsJpaRepository) {
        this.hashtagStatsJpaRepository = hashtagStatsJpaRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void rollupHashtagStats() {
        log.info("Starting hashtag stats weekly rollup");
        hashtagStatsJpaRepository.rollupWeeklyCounts();
        log.info("Hashtag stats rollup complete");
    }
}
