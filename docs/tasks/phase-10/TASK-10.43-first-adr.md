# TASK-10.43 — Write your first ADR (Architecture Decision Record)

## Overview

An Architecture Decision Record (ADR) is a short document — typically one page — that captures a single architectural or technology decision: the problem being solved, the choice made, and the trade-offs accepted. This task introduces the ADR format by having you document one real decision from Phase 10 (for example, choosing RS256 over HS256 for JWT signing, or choosing Redis as the cache layer). Once the format is established, future decisions are quick to record and the whole team benefits from the written context.

## Level

**Warm-up** · Pairs with any decision made during Phase 10. Good candidates: RS256 vs HS256 ([TASK-10.16](TASK-10.16-jwt-hardening.md)), Redis for feed cache ([TASK-10.3](TASK-10.3-redis-caching.md)), ShedLock for distributed jobs ([TASK-10.48](TASK-10.48-shedlock-scheduled-jobs.md)), or multi-stage Dockerfiles ([TASK-10.44](TASK-10.44-dockerfile-backend.md)).

## Why

When a decision is obvious to the person who made it, it looks obvious. Six months later — after team changes, library upgrades, or a production incident — the question "why did we do it this way?" has no good answer if nobody wrote it down. An ADR turns a one-time conversation into a permanent, searchable record. Future engineers (including future you) can read it in two minutes and understand both what was decided and why the alternatives were ruled out, so they can make an informed choice about whether the decision still holds or should be revisited.

## Prerequisites

- No code changes required — this is a documentation task.
- You should have completed (or be actively working on) at least one Phase 10 task, so you have a real decision to document.
- **Concepts to skim:**
  - _ADR format_ — many teams use Michael Nygard's original single-file format: `Title`, `Status`, `Context`, `Decision`, `Consequences`. A good overview is at [adr.github.io](https://adr.github.io/).
  - _What makes a good ADR_ — one decision per file; no longer than one page; written in present tense; the `Context` section explains the forces, not the solution.

## Files to Create / Modify

```
docs/adr/0001-<slug>.md    (new — replace <slug> with a short hyphenated title)
docs/adr/README.md         (new — index file so the folder is discoverable)
```

The `docs/adr/` directory does not exist yet; you will create it.

## Step-by-Step

### 1. Create the `docs/adr/` directory

PowerShell:

```powershell
New-Item -ItemType Directory -Path "docs/adr" -Force
```

Bash:

```bash
mkdir -p docs/adr
```

### 2. Choose a decision to document

Pick one concrete decision from Phase 10. Good first choices (low research required because the project already made the call):

| Decision | Suggested slug |
|---|---|
| Use Redis as the cache layer for feed and profiles (not Memcached, not in-process Caffeine) | `0001-redis-for-feed-cache` |
| Use RS256 (asymmetric) over HS256 (symmetric) for JWT signing | `0001-rs256-jwt-signing` |
| Use ShedLock over Quartz for distributed job locking | `0001-shedlock-for-distributed-jobs` |
| Use multi-stage Docker builds instead of single-stage | `0001-multi-stage-docker-builds` |

Pick one. The example below uses the Redis cache decision.

### 3. Write the ADR file

Create `docs/adr/0001-redis-for-feed-cache.md` (adjust the slug to match your chosen decision):

```markdown
# ADR-0001 — Redis as the cache layer for feed and profile endpoints

**Date:** 2026-05-23
**Status:** Accepted
**Deciders:** [your name / team]
**Relates to:** TASK-10.3 (Redis caching for feed & profiles)

---

## Context

The home-feed query (`GET /api/v1/feed`) joins across `follows`, `posts`,
`post_media`, and `users` to build a personalised timeline. Under read-heavy
traffic this query is expensive: without caching, every scroll event
re-executes the join against PostgreSQL.

We need a caching layer that:
- Has low operational complexity for a single-region deployment.
- Supports key-based invalidation (evict a user's feed cache when they follow
  or unfollow someone).
- Can survive a process restart without warming up from scratch on every deploy.
- Is already part of the local `docker-compose.yml` stack so developers don't
  need an extra service.

Options considered:
1. **In-process Caffeine cache** — zero operational overhead; dies on restart;
   incompatible with horizontal scaling (each instance has a stale view).
2. **Redis** — shared across all backend instances; persists across restarts
   with AOF/RDB; supports TTL and `DEL` for manual invalidation; adds one
   external process.
3. **Memcached** — similar to Redis but lacks native data structures, Lua
   scripting, and built-in persistence. No meaningful advantage for this use
   case.

## Decision

Use **Redis 7** (via `spring-boot-starter-data-redis` + `RedisTemplate`) as
the distributed cache.

- Cache `GET /api/v1/feed` (first page, cursor=null) with a 60-second TTL,
  keyed as `feed:{userId}:page1`.
- Cache `GET /api/v1/users/{username}` with a 5-minute TTL, keyed as
  `profile:{username}`.
- Evict `profile:{username}` when the user updates their profile.

## Consequences

**Positive:**
- Identical cache is shared across all backend instances — adding a second
  replica does not introduce cache incoherence.
- Spring Cache abstraction (`@Cacheable`, `@CacheEvict`) keeps the service
  layer clean; swapping the backing store later requires only config changes.
- Redis is already in `docker-compose.yml` (added in Phase 6 for WebSocket
  pub/sub), so there is no new dependency for local development.

**Negative / Trade-offs:**
- Adds one more process to operate in production. If Redis is down, the
  fallback path hits PostgreSQL for every request (acceptable; it is a cache,
  not a primary store).
- A 60-second feed TTL means a new post can be invisible to a follower for up
  to 60 seconds after publication. This is acceptable for this application's
  consistency requirements.
- Cache entries must be serialised to JSON (`GenericJackson2JsonRedisSerializer`);
  domain model changes require a cache-version bump or a `FLUSHDB` to avoid
  deserialisation errors on stale entries.
```

### 4. Create the ADR index file

Create `docs/adr/README.md` so the directory shows up clearly in GitHub:

```markdown
# Architecture Decision Records

This directory contains ADRs for the Instagram Social Media Platform.

Each file documents one decision: the context (why we needed to decide),
the decision itself, and the consequences (what we gained and accepted).

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](0001-redis-for-feed-cache.md) | Redis as the cache layer for feed and profile endpoints | Accepted |
```

Update this table whenever you add a new ADR.

### 5. Link the ADR directory from the main `docs/` area

Open `docs/plan.md` (or the repo root `README.md`) and add a line pointing to the ADR index so it is discoverable:

```markdown
## Architecture Decision Records

See [`docs/adr/`](docs/adr/) for a log of significant architectural decisions.
```

## Checklist

- [ ] Pick one Phase 10 decision (e.g. "RS256 over HS256" or "Redis for feed cache")
- [ ] Write it up in `docs/adr/0001-<slug>.md` using the Context / Decision / Consequences format
- [ ] Link it from `docs/` so it's discoverable

## How to Verify

1. Open `docs/adr/0001-<slug>.md` on GitHub (or in VS Code's Markdown preview) and confirm it renders cleanly with all three sections visible: **Context**, **Decision**, and **Consequences**.

2. Confirm the index links to it:

   ```powershell
   # The README should contain the filename
   Select-String "0001-" docs/adr/README.md
   ```

   Passing result: the filename appears in the output.

3. Confirm the ADR is linked from the project docs:

   ```powershell
   Select-String "docs/adr" README.md
   ```

   Passing result: the path appears in the output.

## Notes / Gotchas

- **One ADR per decision.** Do not combine "we chose Redis AND multi-stage Docker" into one file. Each decision gets its own file with its own sequential number. This makes it easy to link from code comments (e.g. `// See ADR-0001`) and easy to supersede a specific decision without touching others.

- **"Status" values.** The most useful statuses are `Proposed` (under discussion), `Accepted` (in effect), `Superseded by ADR-XXXX` (replaced by a later decision), and `Deprecated` (the decision still applies but the context no longer makes it relevant). Always fill in `Status` — a blank ADR file is harder to act on.

- **Write in present tense.** Say "We use Redis" not "We decided to use Redis". The ADR represents the current decision, not a historical narrative.

- **Keep it short.** One page is the target. If you are writing more than 400 words in the Decision section, you are documenting implementation details, not a decision. Move those details to `docs/` or a code comment.

- **Number sequentially.** `0001`, `0002`, `0003` — zero-padded to four digits so alphabetical file order matches chronological order.

- Reference: [adr.github.io](https://adr.github.io/) — a community site with templates and tooling; [Michael Nygard's original post](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) introduces the format.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **What an ADR is** — record the *why* behind a decision, not just the *what* — https://github.com/joelparkerhenderson/architecture-decision-record
- **ADR templates** — a simple, repeatable structure — https://adr.github.io/
- **MADR (Markdown ADR)** — a popular lightweight format — https://adr.github.io/madr/

### Official docs (code reference)
- **adr-tools (CLI)** — https://github.com/npryce/adr-tools
- **ADR organization site** — https://adr.github.io/
