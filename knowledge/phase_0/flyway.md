# Flyway

## 1. What is it?

**Flyway** is a database migration tool. It manages changes to your database schema (tables, columns, indexes, constraints, seed data, etc.) as a series of versioned, ordered SQL (or Java) scripts, and tracks which ones have already been applied via a metadata table (`flyway_schema_history`).

Think of it as "Git for your database schema": every change is a numbered, immutable script committed to source control, and Flyway applies them in order, once, on every environment.

Key building blocks:
- **Migration scripts** — files named with a strict convention, e.g. `V1__create_users_table.sql`, `V2__add_email_index.sql`. The `V` prefix means "versioned", the number is the version, `__` separates version from description.
- **`flyway_schema_history` table** — Flyway creates this in your database to record which migrations have run, their checksum, and when.
- **Migration types**:
  - `V` (Versioned) — runs once, in order, never re-applied.
  - `U` (Undo) — optional rollback scripts (paid/enterprise feature in some versions).
  - `R` (Repeatable) — re-run every time their checksum changes (good for views, stored procedures).
- **Checksum validation** — Flyway detects if an already-applied migration file was edited after the fact and fails fast instead of silently applying a modified script.

## 2. Why use it?

- **Reproducible schema across environments**: dev, test, staging, prod all end up with the exact same schema, applied in the exact same order.
- **Version control for the database**: schema changes live in Git alongside the code that depends on them — you can see history, diff, and blame just like application code.
- **Safety**: prevents "it works on my machine" schema drift; a migration that ran in dev is guaranteed to run identically in prod.
- **Team collaboration**: multiple developers can add migrations independently without manually syncing SQL scripts or stepping on each other's schema changes.
- **Automated, repeatable deployments**: schema updates become part of the CI/CD pipeline (often auto-applied on app startup) instead of a manual DBA step.
- **Auditability**: `flyway_schema_history` is a full audit trail of every schema change ever applied, with checksums to detect tampering.

## 3. How can you use it?

**a) Add the dependency (Spring Boot + PostgreSQL example)**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```
Spring Boot auto-configures Flyway and runs pending migrations automatically on application startup — no extra code needed.

**b) Migration file naming convention**
```
src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__create_posts_table.sql
├── V3__add_index_on_posts_user_id.sql
└── R__refresh_user_stats_view.sql
```
- `V<version>__<description>.sql` for versioned (one-time) migrations.
- `R__<description>.sql` for repeatable migrations (re-run whenever content changes).

**c) Example migration script**
```sql
-- V2__create_posts_table.sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    caption VARCHAR(2200),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_posts_user_id ON posts(user_id);
```

**d) Common configuration (`application.yml`)**
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # allow Flyway onto an existing non-empty DB
    validate-on-migrate: true   # fail startup if applied scripts were changed
```

**e) Rules to follow**
- **Never edit or delete an already-applied migration** — it will fail the checksum check on other environments. Add a new migration instead.
- **Migrations run in order and are immutable once applied** — treat them like committed Git history.
- **Run migrations automatically on app startup** (default with Spring Boot) or manually via the Flyway CLI/Maven/Gradle plugin (`flyway migrate`, `flyway info`, `flyway repair`).

**f) Useful CLI/Maven commands**
```bash
mvn flyway:info      # show migration status
mvn flyway:migrate   # apply pending migrations
mvn flyway:validate  # check applied migrations against local files
mvn flyway:repair    # fix metadata after a failed/edited migration
```

## 4. When to use it in real life

- **Every project with a relational database that evolves over time** — essentially any non-trivial Spring Boot / Java backend with PostgreSQL, MySQL, etc.
- **Team environments** — so schema changes are reviewed in PRs (as SQL files) instead of manually run by whoever remembers to do it.
- **CI/CD pipelines** — migrations run automatically when the app deploys, keeping schema and code version in lockstep release after release.
- **Multi-environment setups** (dev/test/staging/prod) — guarantees the same schema state everywhere, avoiding "works in dev, breaks in prod" bugs caused by manual DB changes.
- **Integration tests with Testcontainers** — Flyway runs against a fresh containerized DB before tests execute, so tests always run against the real, current schema.
- **Incremental schema evolution** — adding a column, new table, or index for a new feature without writing manual migration/rollback scripts by hand.
- **Auditing/compliance** — when you need a verifiable, ordered history of every schema change made to a production database.

---

## Summary

Flyway is a database migration tool that manages schema changes as versioned, ordered SQL scripts, tracked in a metadata table (`flyway_schema_history`), so every environment ends up with an identical, reproducible database schema.

- **What**: Versioned SQL/Java migration scripts (`V1__desc.sql`, `R__desc.sql`) applied in order and tracked via a schema history table with checksums.
- **Why**: Keeps schema changes version-controlled, reproducible across environments, safe from drift/tampering, and auditable — schema evolves alongside application code instead of via manual DBA steps.
- **How**: Add `flyway-core` (+ DB dialect dependency), place migration files under `db/migration`, let Spring Boot auto-run them on startup, and never modify an already-applied migration — always add a new one.
- **When**: Any project with a relational database that changes over time — team collaboration via PRs, CI/CD auto-deploy, multi-environment consistency, Testcontainers-based integration tests, and compliance/audit trails.
