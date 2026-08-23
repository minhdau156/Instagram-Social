package com.instagram.infrastructure.config;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;


@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisConfig {

        private GenericJackson2JsonRedisSerializer jsonSerializer() {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new PageJacksonModule());
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                mapper.activateDefaultTyping(
                                BasicPolymorphicTypeValidator.builder()
                                                .allowIfSubType(Object.class)
                                                .build(),
                                ObjectMapper.DefaultTyping.EVERYTHING,
                                JsonTypeInfo.As.PROPERTY);
                return new GenericJackson2JsonRedisSerializer(mapper);
        }

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
                GenericJackson2JsonRedisSerializer serializer = jsonSerializer();
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(factory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(serializer);
                template.setHashKeySerializer(new StringRedisSerializer());
                template.setHashValueSerializer(serializer);
                return template;
        }

        @Bean(name = "redisCacheManager")
        @Primary
        public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
                GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();

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

        /**
         * {@link PageImpl} has no default/delegate constructor Jackson can use, so under
         * {@code activateDefaultTyping} it fails to deserialize from Redis with
         * "Cannot construct instance of PageImpl". Spring Data doesn't ship a matching
         * creator either (Pageable/Sort aren't Jackson-friendly), so this module flattens
         * a cached page to {content, number, size, totalElements} and rebuilds an
         * unsorted PageRequest from those on read — sort order isn't preserved across a
         * cache hit, which is an acceptable trade-off for cached list endpoints.
         */
        private static final class PageJacksonModule extends SimpleModule {
                PageJacksonModule() {
                        super("PageJacksonModule");
                        addSerializer(PageImpl.class, new PageImplSerializer());
                        addDeserializer(PageImpl.class, new PageImplDeserializer());
                }
        }

        @SuppressWarnings("rawtypes")
        private static final class PageImplSerializer extends JsonSerializer<PageImpl> {

                @Override
                public void serialize(PageImpl value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                        gen.writeStartObject();
                        writeBody(value, gen);
                        gen.writeEndObject();
                }

                @Override
                public void serializeWithType(PageImpl value, JsonGenerator gen, SerializerProvider serializers,
                                TypeSerializer typeSer) throws IOException {
                        WritableTypeId typeId = typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
                        writeBody(value, gen);
                        typeSer.writeTypeSuffix(gen, typeId);
                }

                private void writeBody(PageImpl<?> value, JsonGenerator gen) throws IOException {
                        gen.writeObjectField("content", value.getContent());
                        gen.writeNumberField("number", value.getNumber());
                        gen.writeNumberField("size", value.getSize());
                        gen.writeNumberField("totalElements", value.getTotalElements());
                }
        }

        @SuppressWarnings("rawtypes")
        private static final class PageImplDeserializer extends JsonDeserializer<PageImpl> {

                @Override
                public PageImpl<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                        JsonNode node = p.getCodec().readTree(p);
                        JavaType listType = ctxt.getTypeFactory().constructCollectionType(List.class, Object.class);
                        List<?> content = node.hasNonNull("content")
                                        ? ctxt.readTreeAsValue(node.get("content"), listType)
                                        : List.of();
                        int number = node.path("number").asInt(0);
                        int size = Math.max(node.path("size").asInt(content.size()), 1);
                        long totalElements = node.path("totalElements").asLong(content.size());
                        return new PageImpl<>(content, PageRequest.of(number, size), totalElements);
                }
        }
}
