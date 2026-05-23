# Phase 11 — Event-Driven Architecture & Async Messaging

> **Track:** Backend · **Depends on:** Phases 4, 7 · **New tools:** Apache Kafka (or Redpanda), Spring Kafka, Testcontainers-Kafka  
> **Branch prefix:** `feat/phase-11-`

---

> **Skills you'll build:**
> - Designing domain events vs. integration events
> - The transactional outbox pattern — no lost events when the DB commit succeeds but the broker publish fails
> - Idempotent consumers under at-least-once delivery
> - Async fan-out, consumer groups, and backpressure
>
> **Best practices:** events are immutable past-tense facts (`PostLiked`, not `LikePost`); version your event schema from day one; consumers must be idempotent; never publish to a broker *inside* a DB transaction — use the outbox.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Infrastructure

### TASK-11.1 — Add Kafka (or Redpanda) to `docker-compose.yml`
> **Why:** Async messaging needs a broker; running one locally lets you publish and consume events without touching the request path.
> **Done when:** `docker compose up` starts the broker healthy and you can list topics with the broker CLI (e.g. `kafka-topics --list` / `rpk topic list`).
- [ ] Add a `kafka` (or `redpanda`) service to `docker-compose.yml` with exposed broker port and a `healthcheck`
- [ ] Add `spring-kafka` to `backend/pom.xml`
- [ ] Configure `spring.kafka.bootstrap-servers` and consumer/producer defaults in `application.yml`
- [ ] Verify the backend connects on boot (no broker-connection errors in logs)

---

## Domain Events

### TASK-11.2 — Define domain event records (`PostCreated`, `PostLiked`, `UserFollowed`, `CommentAdded`)
> **Why:** Events are the contract between producers and consumers — modeling them as immutable past-tense facts keeps the system decoupled and the schema explicit.
> **Done when:** Each event is a pure Java `record` with a stable `eventId`, `occurredAt`, and a `schemaVersion`, and the project compiles with no framework imports in `domain/event/`.
- [ ] Create `domain/event/DomainEvent.java` sealed interface (fields: `eventId`, `occurredAt`, `schemaVersion`)
- [ ] Create `domain/event/PostCreated.java`, `PostLiked.java`, `UserFollowed.java`, `CommentAdded.java` as records implementing it
- [ ] Keep these pure Java — no Spring/JPA/Lombok annotations (per coding-standard)
- [ ] Add a unit test asserting each event carries a non-null `eventId` and `schemaVersion`

---

## Transactional Outbox

### TASK-11.3 — `outbox` table + Flyway migration
> **Why:** Writing the event to the same DB transaction as the business change guarantees the event is never lost if the broker publish later fails.
> **Done when:** Flyway applies the migration cleanly and an `outbox` row is inserted in the same transaction as a new post (verify with `SELECT * FROM outbox`).
- [ ] Create `backend/src/main/resources/db/migration/V4__add_outbox.sql` (columns: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload jsonb`, `created_at`, `published_at`, `status`)
- [ ] Add `OutboxJpaEntity.java` + `OutboxJpaRepository.java` in `adapter/out/persistence/`
- [ ] Add an out-port `domain/port/out/EventPublisher.java` and an `OutboxEventPersistenceAdapter` that writes a row instead of publishing directly
- [ ] Wire `PostService`/`LikeService`/`FollowService`/`CommentService` to record events via the out-port inside their existing transaction

### TASK-11.4 — Outbox relay (poller) that publishes committed events to Kafka
> **Why:** A separate relay reads committed outbox rows and publishes them, so publishing happens *after* the transaction commits — never inside it.
> **Done when:** Creating a post results in the matching event landing on its Kafka topic, and the `outbox` row's `published_at` is set.
- [ ] Create `adapter/out/messaging/OutboxRelay.java` with a `@Scheduled` poller selecting unpublished rows
- [ ] Publish each row to its Kafka topic via a `KafkaTemplate` wrapper in `adapter/out/messaging/`
- [ ] Mark rows published (`status`/`published_at`) only after a successful send
- [ ] Confirm a consumed test message arrives after the publishing transaction commits

---

## Consumers

### TASK-11.5 — Replace the `@EventListener` notification handler with a Kafka consumer
> **Why:** Moving notifications onto a Kafka consumer decouples them from the request thread and lets them scale and retry independently.
> **Done when:** A like/follow/comment produces a notification driven entirely by a Kafka consumer (the old `@EventListener` path no longer fires).
- [ ] Add `adapter/in/messaging/NotificationKafkaConsumer.java` subscribing to the relevant event topics
- [ ] Have it call the existing `CreateNotificationUseCase` in-port
- [ ] Remove or disable the legacy `NotificationEventHandler` `@EventListener` wiring from `infrastructure/event/`
- [ ] Verify notifications still appear end-to-end through the new consumer

### TASK-11.6 — Idempotency: dedupe consumed events by event id
> **Why:** At-least-once delivery means consumers can see the same event twice; dedup prevents duplicate notifications/feed entries.
> **Done when:** Re-delivering the same `eventId` produces exactly one side-effect (no duplicate notification row).
- [ ] Create `backend/src/main/resources/db/migration/V5__add_processed_events.sql` with a `processed_events` table keyed on `event_id` + `consumer_group`
- [ ] Add a `ProcessedEventPersistenceAdapter` and check-then-record helper in `adapter/out/persistence/`
- [ ] Skip processing when the `(event_id, consumer_group)` is already recorded
- [ ] Add a test that delivers a duplicate event and asserts a single side-effect

### TASK-11.7 — Feed fan-out-on-write consumer
> **Why:** Fanning a new post out to followers' feeds asynchronously keeps post creation fast and moves the heavy write off the request path.
> **Done when:** Creating a post inserts feed entries for the author's followers via a Kafka consumer (verify follower feed rows appear shortly after).
- [ ] Add `adapter/in/messaging/FeedFanoutConsumer.java` subscribing to `PostCreated`
- [ ] Look up the author's followers and write per-follower feed entries (reuse/extend the feed persistence adapter)
- [ ] Make it idempotent via the TASK-11.6 dedupe table
- [ ] Verify a follower sees the new post in their feed after creation

---

## Tests

### TASK-11.8 — Integration tests with Testcontainers-Kafka
> **Why:** Mocks can't prove publish/consume actually works; a real broker in a container catches serialization, topic, and consumer-group bugs.
> **Done when:** An integration test boots a Kafka container, publishes via the outbox relay, and asserts the consumer produced the expected side-effect.
- [ ] Add `org.testcontainers:kafka` (test scope) to `pom.xml`
- [ ] Create `OutboxRelayIT` / `NotificationKafkaConsumerIT` using `@Container KafkaContainer`
- [ ] Assert the outbox row is published and the consumer side-effect occurs
- [ ] Add a test asserting duplicate delivery is deduped (covers TASK-11.6)
