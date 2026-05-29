# TASK-10.2 — Tune the HikariCP connection pool

## Overview

Spring Boot's default JDBC connection pool is HikariCP. Out of the box it creates up to 10 connections, which is a reasonable guess but rarely the right number for your specific hardware. This task teaches you to think about pool sizing properly, set `maximum-pool-size` explicitly in `application.yml`, and verify the pool's behavior under load through Spring Boot Actuator's built-in metrics. The change is a single YAML property, but the reasoning behind the number is the real learning outcome.

---

## Level

Warm-up · Pairs with [TASK-10.3 Redis caching](TASK-10.3-redis-caching.md)

---

## Why

A connection pool holds a fixed set of live database connections so threads don't pay the cost of opening a new connection for every request. The right pool size is not "as large as possible" — each Postgres connection uses server-side RAM and a process slot, and PostgreSQL starts degrading under heavy connection load well before it runs out of connections entirely. The common formula is `(number of CPU cores) * 2 + 1` for most web workloads, but the real answer comes from watching the metrics: if `hikaricp.connections.pending` is consistently above zero, the pool is too small; if `hikaricp.connections.active` never exceeds 3 on a 10-connection pool, you're holding resources Postgres doesn't need to give you.

---

## Prerequisites

- TASK-10.1 complete — you already have the baseline query plan. The pool size affects how quickly those queries get a connection.
- The application is running (`cd backend && mvn spring-boot:run`).
- Spring Boot Actuator is (or will be) on the classpath. If not yet added, TASK-10.27 covers that — for this task you only need the Actuator dependency present. Check `pom.xml` for `spring-boot-starter-actuator`. If it is absent, add it temporarily.

**Concepts to skim:**
- HikariCP: the default JDBC connection pool bundled with Spring Boot 2.x and 3.x. Named for its speed ("hikari" = light in Japanese).
- Connection pool: a pre-created set of open JDBC connections that threads borrow and return, avoiding reconnect overhead on every request.
- `maximum-pool-size`: the hard upper limit on connections HikariCP will open. Requests that need a connection when all are in use wait in a queue until one is returned.
- `minimum-idle`: the number HikariCP keeps open even when idle. Defaults to `maximum-pool-size` (fixed pool). Leave it at the default for this task.

---

## Files to Create / Modify

```
backend/src/main/resources/application.yml           (modify)
backend/src/main/resources/application-local.yml     (modify — local overrides)
```

---

## Step-by-Step

### 1. Check the current pool configuration

Open `backend/src/main/resources/application.yml`. Search for any existing `spring.datasource.hikari` section. As of the project's current state there is none, which means HikariCP uses its built-in default of `maximum-pool-size=10`.

Confirm by starting the app and hitting the Actuator metrics endpoint (add `spring-boot-starter-actuator` to `pom.xml` first if it is not present — see TASK-10.27):

```powershell
# While the app is running
Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.max
```

Expected output (if using the default):
```json
{
  "name": "hikaricp.connections.max",
  "measurements": [{ "statistic": "VALUE", "value": 10.0 }]
}
```

---

### 2. Understand the sizing formula

The widely-cited HikariCP recommendation is:

```
pool_size = (effective_spindle_count * 2) + effective_spindle_count
```

For a modern dev machine with a single SSD, HikariCP's own documentation simplifies this to roughly:

```
pool_size = (cpu_cores * 2) + 1
```

Find how many logical CPU cores your machine has:

```powershell
(Get-CimInstance Win32_ComputerSystem).NumberOfLogicalProcessors
```

For a typical developer laptop with 8 logical cores:

```
pool_size = (8 * 2) + 1 = 17  →  round to 10–15 for local dev
```

For production on a machine dedicated to the web tier, start at `10` and tune up based on observed `hikaricp.connections.pending` metrics.

---

### 3. Add the explicit pool configuration

Open `backend/src/main/resources/application.yml` and add the `hikari` section under `spring.datasource`. Place it after any existing datasource settings:

```yaml
spring:
  datasource:
    hikari:
      # Formula: (cpu_cores * 2) + 1; capped at 10 for local dev to avoid
      # overwhelming Postgres with connections on a shared developer machine.
      # Raise to 15-20 in production once connection metrics are observed.
      maximum-pool-size: 10
      minimum-idle: 10          # fixed-size pool — avoids idle-connection teardown churn
      connection-timeout: 30000 # ms — how long a thread waits for a free connection
      idle-timeout: 600000      # ms — how long an idle connection is kept (10 min)
      max-lifetime: 1800000     # ms — max age of a connection (30 min), below Postgres default
      pool-name: InstagramPool
```

**Why `minimum-idle: 10` equals `maximum-pool-size`?**
This creates a fixed-size pool. HikariCP's author recommends this over a variable pool because idle connections are cheap (the server is already open) and the overhead of constantly closing and reopening them (variable pool) is not worth the saved memory on a small service.

---

### 4. Add an environment-specific override for production

In `backend/src/main/resources/application-local.yml`, you can override the pool size to a smaller value for local development if needed:

```yaml
# Local dev: keep the pool small so a single developer's Postgres
# isn't flooded when running multiple backend instances side by side.
spring:
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
```

The `application.yml` value serves as the production-sensible default; the local profile reduces it for dev machines.

---

### 5. Restart and verify the new pool size

Stop and restart the backend:

```powershell
# In the backend directory
mvn spring-boot:run
```

Then query the Actuator metric again:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.max
```

Expected output:
```json
{
  "name": "hikaricp.connections.max",
  "measurements": [{ "statistic": "VALUE", "value": 10.0 }]
}
```

Also check the active connections (should be low at idle):

```powershell
Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

```json
{
  "name": "hikaricp.connections.active",
  "measurements": [{ "statistic": "VALUE", "value": 0.0 }]
}
```

---

### 6. Observe the pool under load (optional but recommended)

If you want to see the pool metrics under actual load, use a simple loop to hit the feed endpoint while watching the Actuator:

```powershell
# Terminal 1 — fire 50 concurrent-ish requests
1..50 | ForEach-Object -Parallel {
    Invoke-WebRequest -Uri "http://localhost:8080/api/v1/feed" `
                      -Headers @{Authorization="Bearer <your_token>"} `
                      -UseBasicParsing -ErrorAction SilentlyContinue
} -ThrottleLimit 10

# Terminal 2 — watch pool metrics
while ($true) {
    $active = (Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.active).measurements[0].value
    $pending = (Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.pending).measurements[0].value
    Write-Host "active=$active pending=$pending"
    Start-Sleep 1
}
```

If `pending` climbs above 0 regularly, increase `maximum-pool-size`. If `active` never exceeds 2-3, you could lower the pool.

---

## Checklist

- [x] Read the Hikari docs on pool sizing (start around `CPU cores * 2`)
- [x] Set `maximum-pool-size` explicitly and add a comment explaining your reasoning
- [x] Watch the pool metrics under load via `/actuator/metrics/hikaricp.connections.active`

---

## How to Verify

**Minimum verification:**

```powershell
# 1. The property is set in the YAML
Select-String -Path backend/src/main/resources/application.yml -Pattern "maximum-pool-size"
```

Passing output: a line like `maximum-pool-size: 10` with a comment on the line above.

**Full verification (requires Actuator):**

```powershell
# 2. The metric reflects the new value
Invoke-RestMethod http://localhost:8080/actuator/metrics/hikaricp.connections.max
```

Passing output: `"value": 10.0` (or whatever you set).

**Startup log verification:**

In the backend startup log you should see a HikariCP info line:

```
HikariPool-1 - Start completed.
```

If the pool cannot connect to Postgres you will see:

```
HikariPool-1 - Exception during pool initialization.
```

This means Postgres is not running — start it with `docker compose up postgres`.

---

## Notes / Gotchas

**"Actuator endpoint returns 404."**
`spring-boot-starter-actuator` must be on the classpath and the endpoints must be exposed. Add the dependency to `pom.xml` (see TASK-10.27) and add this to `application-local.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**"The HikariCP metric doesn't appear under `/actuator/metrics`."**
Make sure the `pool-name` property is set. HikariCP registers metrics under the pool name prefix. If `pool-name` is absent, the metric name will use the default (`HikariPool-1`) but the metric key in Micrometer will still be `hikaricp.connections.*`.

**"The pool logs `Connection is not available, request timed out after 30000ms`."**
This means all connections were busy for 30 seconds. Either the pool is too small, a query is holding a connection for an unusually long time (long transaction), or there is a connection leak (a connection was borrowed and never returned). Check whether any service method is annotated `@Transactional` and calls an expensive external API inside the transaction — that holds the DB connection for the entire external call duration.

**Official HikariCP pool sizing docs:**
https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing

**Cross-task references:**
- TASK-10.27 adds the full Actuator + Micrometer setup needed to expose `/actuator/metrics`.
- TASK-10.3 adds Redis caching, which reduces the number of database hits and thus reduces how often the pool is under pressure.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Connection pooling basics** — why reusing connections beats reconnecting per request — https://www.baeldung.com/java-connection-pooling
- **Pool sizing math** — the counter-intuitive "fewer connections can be faster" result — https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
- **Spring Boot + HikariCP** — wiring and tuning the default pool — https://www.baeldung.com/spring-boot-hikari
- **Leak detection & timeouts** — `leakDetectionThreshold`, `connectionTimeout`, `maxLifetime` — https://github.com/brettwooldridge/HikariCP

### Official docs (code reference)
- **HikariCP (README + config knobs)** — https://github.com/brettwooldridge/HikariCP
- **Accessing data with JPA (Spring guide)** — https://spring.io/guides/gs/accessing-data-jpa/
