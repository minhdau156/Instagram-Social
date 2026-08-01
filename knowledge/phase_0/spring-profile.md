# Spring Profile

## 1. What is it?

A **Spring Profile** is a way to group beans, configuration, and property values so that they are only loaded/active under a specific environment (e.g. `dev`, `test`, `staging`, `prod`).

Think of it as a labeled "environment switch" inside your Spring application. Instead of one giant config that tries to handle every environment with `if/else` logic, you tag pieces of configuration with a profile name, and Spring only activates the ones matching the currently active profile(s).

Key building blocks:
- `@Profile("dev")` — annotation on a `@Component`, `@Configuration`, or `@Bean` method to restrict when it's loaded.
- `application-{profile}.yml` / `application-{profile}.properties` — profile-specific property files (e.g. `application-dev.yml`, `application-prod.yml`).
- `spring.profiles.active` — the property that tells Spring which profile(s) to activate.

## 2. Why use it?

- **Environment separation without code duplication**: same codebase, different behavior per environment (different DB URL, logging level, mock vs real external service, etc.).
- **Safety**: prevents accidentally using production secrets/config in local development, or dev-only beans (like test data seeders) leaking into production.
- **Testability**: lets you swap in fakes/mocks or an in-memory database (e.g. H2) only for the `test` profile, while `prod` uses PostgreSQL.
- **Single deployable artifact**: you build one JAR and just change which profile is active at runtime — no need to rebuild per environment.

## 3. How can you use it?

**a) Activate a profile**
- Command line: `java -jar app.jar --spring.profiles.active=prod`
- Environment variable: `SPRING_PROFILES_ACTIVE=prod`
- In `application.yml`: `spring.profiles.active: dev`
- In tests: `@ActiveProfiles("test")` on the test class.

**b) Profile-specific property files**
```
application.yml          # common/shared config
application-dev.yml      # overrides for dev
application-prod.yml     # overrides for prod
```
Spring loads `application.yml` first, then layers the active profile's file on top (profile values win on conflict).

**c) Conditional beans**
```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSource devDataSource() {
        return new EmbeddedDatabaseBuilder().setType(H2).build();
    }

    @Bean
    @Profile("prod")
    public DataSource prodDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:postgresql://prod-host/db")
                .build();
    }
}
```

**d) Multiple/combined profiles**
- Activate several at once: `spring.profiles.active=prod,metrics`
- Profile expressions: `@Profile("prod & !disable-cache")`

## 4. When to use it in real life

- **Local dev vs CI vs staging vs production** — different DB connections, API keys, logging verbosity, feature flags.
- **Integration tests** — activate a `test` profile that points to Testcontainers/H2 instead of a real external service (this is exactly what `application-test.yml` + `@ActiveProfiles("test")` is used for in Spring Boot test classes).
- **Feature toggling per environment** — e.g. enabling detailed Swagger/OpenAPI docs only in `dev`, disabling them in `prod`.
- **Mocking external dependencies** — use a `local` profile that stubs out third-party APIs (payment gateway, email service) so developers can run the app without real credentials.
- **Multi-tenant or multi-region deployment variants** — separate profiles per region/tenant with different config values.

---

## Summary

Spring Profiles let you activate different beans and configuration values depending on the environment the application is running in (dev, test, prod, etc.), without duplicating code or maintaining multiple codebases.

- **What**: A labeling mechanism (`@Profile`, `application-{profile}.yml`, `spring.profiles.active`) that scopes beans/config to specific environments.
- **Why**: Avoids hardcoded environment logic, keeps secrets/config separated, enables safe testing with mocks/in-memory resources, and ships one artifact across all environments.
- **How**: Annotate beans with `@Profile("name")`, create `application-{profile}.yml` files for overrides, and activate via `spring.profiles.active` (CLI arg, env var, config file, or `@ActiveProfiles` in tests).
- **When**: Switching DB/config per environment, running integration tests against fakes/Testcontainers, toggling dev-only tools (Swagger, seed data), and mocking external services locally.
