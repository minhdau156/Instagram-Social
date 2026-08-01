# Redis Caching

## What is it
Redis (Remote Dictionary Server) is an in-memory, key-value data store. Used as a cache, it sits between the application and a slower backing store (usually a relational database), holding frequently-accessed data in RAM so reads don't have to hit disk-based storage every time.

## Why use it
- **Speed**: RAM access is orders of magnitude faster than disk-based DB queries (microseconds vs milliseconds).
- **Reduce DB load**: Offloads repeated read queries from the primary database, protecting it from traffic spikes.
- **Scalability**: Lets an application handle far more concurrent users without scaling the database itself.
- **Rich data structures**: Beyond simple strings, Redis supports hashes, lists, sets, sorted sets, and streams — useful for more than just caching (e.g., rate limiting, leaderboards, pub/sub).
- **TTL support**: Built-in expiration lets you avoid serving stale data indefinitely.

## How can use it
1. Add Redis as a dependency (e.g., Spring Boot: `spring-boot-starter-data-redis`).
2. Configure connection (host/port/password) in `application.yml`.
3. Common patterns:
   - **Cache-aside (lazy loading)**: App checks Redis first; on miss, reads DB, then writes result to Redis with a TTL.
   - **Write-through**: App writes to Redis and DB at the same time.
   - **Annotation-based (Spring)**: `@Cacheable`, `@CachePut`, `@CacheEvict` on service methods, backed by `RedisCacheManager`.
4. Set appropriate TTL/eviction policy to prevent unbounded memory growth and stale data.
5. Use serialization (JSON via Jackson, or Redis's own binary format) for storing objects.

## When use it in real life
- Caching expensive/frequent DB queries (e.g., product catalogs, user profiles, config data).
- Session storage for distributed web applications (so any server instance can read a user's session).
- Rate limiting / throttling API requests.
- Leaderboards and real-time counters (using sorted sets/increments).
- Pub/Sub messaging between microservices.
- De-duplication or idempotency checks (e.g., "has this webhook already been processed?").
