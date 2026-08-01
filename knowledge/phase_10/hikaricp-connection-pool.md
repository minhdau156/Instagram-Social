# HikariCP Connection Pool

## 1. What is it?

**HikariCP** is a JDBC **connection pool** library — it maintains a pool of pre-opened, reusable database connections instead of opening/closing a fresh TCP + auth handshake to the database on every query. It's the **default connection pool in Spring Boot** (since Spring Boot 2.x) when a JDBC starter (`spring-boot-starter-data-jpa`, `spring-boot-starter-jdbc`) is on the classpath.

Key configuration knobs (via `spring.datasource.hikari.*` in `application.yml`/`.properties`):
- **`maximum-pool-size`** — max number of connections the pool will ever hold (default 10). This is the "10" commonly referenced — HikariCP's default max pool size is 10.
- **`minimum-idle`** — minimum number of idle connections HikariCP tries to keep ready (defaults to same as max-pool-size).
- **`connection-timeout`** — how long a thread waits for a connection from the pool before throwing `SQLTransientConnectionException` (default 30s).
- **`idle-timeout`** — how long an idle connection sits before being retired (only applies if `minimum-idle` < `maximum-pool-size`).
- **`max-lifetime`** — max time a connection is kept before being retired/replaced, even if in use (default 30 min) — protects against DB-side connection limits/stale connections.
- **`leak-detection-threshold`** — logs a warning if a connection is checked out longer than this without being returned (helps catch code that forgets to close/release connections).

## 2. Why use it?

- **Performance** — opening a new DB connection is expensive (TCP handshake, auth, session setup — can be tens to hundreds of ms). Pooling reuses already-established connections, cutting this cost to near-zero per request.
- **Resource control** — databases have a hard limit on concurrent connections (e.g., Postgres default `max_connections=100`). A pool caps how many connections your app opens, preventing it from exhausting the database under load or when many app instances share one DB.
- **Fastest pool on the JVM** — HikariCP is benchmarked as one of the lowest-overhead pools (bytecode-level micro-optimizations, minimal locking), which is why Spring Boot made it the default over older pools (Tomcat JDBC, Commons DBCP2, C3P0).
- **Fail-fast behavior & observability** — built-in connection validation, leak detection, and metrics (via Micrometer) surface pool exhaustion or misbehaving code early instead of degrading silently.

## 3. How can you use it?

**It's auto-configured** — if `spring-boot-starter-data-jpa` (or `-jdbc`) plus a JDBC driver are on the classpath, Spring Boot wires a `HikariDataSource` automatically. No extra dependency needed.

**Tune it in `application.yml`:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/instagram_db
    username: app_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000        # ms
      idle-timeout: 600000             # ms (10 min)
      max-lifetime: 1800000            # ms (30 min)
      leak-detection-threshold: 60000  # ms — warn if a connection is held > 60s
      pool-name: InstagramHikariPool
```

**Check the pool is active at startup** — Hikari logs its config and pool name on boot:
```
com.zaxxer.hikari.HikariDataSource : InstagramHikariPool - Starting...
com.zaxxer.hikari.HikariDataSource : InstagramHikariPool - Start completed.
```

**Expose pool metrics** (with `spring-boot-starter-actuator` + Micrometer) to watch active/idle/pending connections at `/actuator/metrics/hikaricp.connections.active`, etc. — critical for sizing decisions.

**Sizing formula (from HikariCP's own docs)** — a common starting point:
```
connections = ((core_count * 2) + effective_spindle_count)
```
In practice, most web apps do fine with a small pool (10–20); a bigger pool is *not* automatically faster — it just adds thread contention on both the app and DB side.

## 4. When to use it in real life

- **Every Spring Boot service that talks to a relational DB** — it's the default, so unless you have a specific reason (e.g., a legacy pool tied into an app server), you keep it.
- **Sizing for load** — when profiling shows requests blocking waiting for a connection (`SQLTransientConnectionException: Connection is not available`), it means the pool's `maximum-pool-size` is too small for your concurrent request volume, or queries are too slow (holding connections too long) — the fix is often optimizing the slow query first, not just raising the pool size.
- **Multiple app instances behind a DB with a fixed connection cap** — you must divide the DB's `max_connections` across all instances/pools (app pool size × instance count < DB limit), otherwise scaling out horizontally can exhaust the database.
- **Debugging "connection leak" warnings in logs** — `leak-detection-threshold` firing usually points to a code path that opens a connection/transaction (e.g., via `EntityManager`, a native `Connection`) and never closes/commits it — a classic bug in manually-managed JDBC or misused `@Transactional` boundaries.
- **Container/Kubernetes deployments with resource limits** — tune `max-lifetime` below any network/load-balancer idle-connection timeout (e.g., AWS RDS proxy, cloud LB idle timeout) so Hikari retires connections before the infra silently drops them, avoiding "connection reset" errors.
- **Testcontainers / integration tests** — a small `maximum-pool-size` (e.g., 2–5) is usually enough and speeds up test startup/teardown compared to the production default.

---

## Summary

**HikariCP** is the JDBC connection pool that Spring Boot uses by default to reuse database connections instead of opening a new one per query.

- **What**: A lightweight, high-performance JDBC connection pool auto-configured by Spring Boot; default `maximum-pool-size` is 10.
- **Why**: Opening DB connections is expensive and databases cap concurrent connections — pooling reuses connections for speed and caps usage to protect the database, with minimal overhead compared to other pool implementations.
- **How**: Configure via `spring.datasource.hikari.*` properties (`maximum-pool-size`, `connection-timeout`, `max-lifetime`, `leak-detection-threshold`); monitor via Actuator/Micrometer metrics; size based on actual concurrency needs, not guesswork.
- **When**: Default for any Spring Boot app with a relational DB; revisit sizing when you see connection-timeout exceptions under load, when scaling out multiple instances against a shared DB connection limit, or when leak-detection warnings point to unclosed connections/transactions.
