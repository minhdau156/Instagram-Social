package com.instagram.infrastructure.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisConfig {

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(factory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());
                template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
                return template;
        }

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
                GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .serializeKeysWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(jsonSerializer))
                                .disableCachingNullValues();

                Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

                // ── Tier 1 — Critical ──────────────────────────────────────────────────────
                cacheConfigs.put("feed", defaultConfig.entryTtl(Duration.ofSeconds(60)));
                cacheConfigs.put("profile", defaultConfig.entryTtl(Duration.ofMinutes(5)));
                cacheConfigs.put("userStats", defaultConfig.entryTtl(Duration.ofMinutes(5)));
                cacheConfigs.put("userPermissions", defaultConfig.entryTtl(Duration.ofHours(2))); // mid-point of 1–4 h
                cacheConfigs.put("postMedia", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigs.put("conversations", defaultConfig.entryTtl(Duration.ofMinutes(3))); // mid-point of 2–5
                                                                                                  // min

                // ── Tier 2 — High ─────────────────────────────────────────────────────────
                cacheConfigs.put("exploreFeed", defaultConfig.entryTtl(Duration.ofMinutes(12))); // mid-point of 10–15
                                                                                                 // min
                cacheConfigs.put("userPosts", defaultConfig.entryTtl(Duration.ofMinutes(30)));
                cacheConfigs.put("comments", defaultConfig.entryTtl(Duration.ofMinutes(7))); // mid-point of 5–10 min
                cacheConfigs.put("followers", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigs.put("followings", defaultConfig.entryTtl(Duration.ofMinutes(15)));

                // ── Tier 3 — Medium ───────────────────────────────────────────────────────
                cacheConfigs.put("savedPosts", defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigs.put("followRequests", defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigs.put("notifSettings", defaultConfig.entryTtl(Duration.ofHours(1)));
                cacheConfigs.put("userRoles", defaultConfig.entryTtl(Duration.ofHours(2)));
                cacheConfigs.put("roles", defaultConfig.entryTtl(Duration.ofHours(4)));

                return RedisCacheManager.builder(factory)
                                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                                .withInitialCacheConfigurations(cacheConfigs)
                                .build();
        }
}
