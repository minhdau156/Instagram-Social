# Rate Limiting

## What is it
Rate limiting is a technique that restricts how many requests a client (a user, an IP, an API key, a service) can make to a system within a given time window. Once the limit is hit, further requests are rejected, delayed, or throttled — typically returning an HTTP `429 Too Many Requests` response.

Common algorithms:

| Algorithm | How it works |
|---|---|
| Fixed Window | Count requests in a fixed time block (e.g. 100/minute), reset at boundary |
| Sliding Window | Like fixed window, but smooths counts across the window boundary to avoid bursts |
| Token Bucket | Bucket refills with tokens over time; each request consumes a token; allows short bursts |
| Leaky Bucket | Requests queue and are processed at a constant rate, smoothing bursts |

## Why use it
Without limits, a single client (malicious or buggy) can overwhelm your system. Rate limiting is used to:
- Prevent abuse and brute-force attacks (login endpoints, OTP verification)
- Protect backend resources (DB, downstream APIs) from being overloaded
- Enforce fair usage across tenants/users in a multi-tenant system
- Control cost when calling paid third-party APIs
- Defend against DDoS-style traffic spikes at the application layer
- Maintain SLAs by ensuring no single client can degrade service for others

## How to use it

### Spring Boot with Redis (Token Bucket via Bucket4j)
```java
@Bean
public ProxyManager<String> proxyManager(RedisClient redisClient) {
    StatefulRedisConnection<String, byte[]> connection =
        redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    return LettuceBasedProxyManager.builderFor(connection)
        .withExpirationStrategy(
            ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
        .build();
}

public Bucket resolveBucket(String apiKey) {
    Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
        .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1))))
        .build();
    return proxyManager.builder().build(apiKey, configSupplier);
}
```

Filter/interceptor usage:
```java
Bucket bucket = resolveBucket(apiKey);
if (bucket.tryConsume(1)) {
    filterChain.doFilter(request, response);
} else {
    response.setStatus(429);
    response.getWriter().write("Rate limit exceeded");
}
```

### Nginx (fixed window, per IP)
```nginx
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

location /api/ {
    limit_req zone=api_limit burst=20 nodelay;
}
```

### Response headers convention
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1690000000
Retry-After: 30
```

## When to use in real life
- Public APIs (paid or free tiers) to enforce usage quotas per API key
- Login / OTP / password-reset endpoints to prevent brute-force attacks
- Search or expensive-query endpoints that hit the database hard
- Microservices calling each other, to prevent cascading overload
- Webhooks or scheduled jobs hitting third-party APIs with their own rate limits (e.g. avoiding 429s from external services)
- Comment/like/follow actions on social apps (like your Instagram-like project) to prevent spam bots
