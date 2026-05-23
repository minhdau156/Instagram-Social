# Phase 17 — Stories & Reels

> **Track:** Product · **Depends on:** Phases 2, 5 · **New tools:** FFmpeg (transcoding), a scheduled expiry job  
> **Branch prefix:** `feat/phase-17-`

---

> **Skills you'll build:**
> - Modeling time-bounded (TTL) content
> - Video upload → transcode → multiple renditions (HLS)
> - View / "seen" tracking at scale
> - Background job processing
>
> **Best practices:** transcode asynchronously (never block the upload request); store renditions + a manifest; expire via a scheduled job *and* a read-time filter; track views idempotently.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Backend

### TASK-17.1 — `stories` table + 24h expiry (migration)
> **Why:** Stories are ephemeral, so the data model must carry an explicit expiry timestamp from the moment a story is created.
> **Done when:** A Flyway migration applies cleanly and `\d stories` in `psql` shows `expires_at` defaulting to `created_at + interval '24 hours'`.
- [ ] Add Flyway migration `backend/src/main/resources/db/migration/V<n>__create_stories.sql` with columns `id UUID`, `user_id`, `media_url`, `media_type`, `created_at`, `expires_at`
- [ ] Index `(user_id, expires_at)` to support active-story lookups
- [ ] Add the `Story` domain model in `domain/model/Story.java` (pure Java, hand-written builder, `isExpired()` behaviour method)
- [ ] Add `StoryNotFoundException` in `domain/exception/`

### TASK-17.2 — Story create/upload via presigned media
> **Why:** Reusing the existing MinIO presigned-URL flow keeps story media off the request path while still recording the story metadata server-side.
> **Done when:** `POST /api/v1/stories` with an uploaded media key returns `201` and the row appears in `stories` with a fresh 24h `expires_at`.
- [ ] Add out-port `StoryRepository` (`domain/port/out/`) and in-port `CreateStoryUseCase` (`domain/port/in/story/`) with a `Command` record
- [ ] Implement `StoryService` in `application/usecase/` (or `domain/service/`) wiring the create flow to the presigned media adapter
- [ ] Add `StoryJpaEntity`, `StoryJpaRepository`, `StoryPersistenceAdapter` in `adapter/out/persistence/` with private `toEntity`/`toDomain` mappers
- [ ] Add `StoryController` (`adapter/in/web/`) + `CreateStoryRequest` / `StoryResponse` DTOs in `adapter/in/web/dto/`

### TASK-17.3 — Story viewer + "seen" tracking
> **Why:** Knowing who viewed a story (and not double-counting a re-open) powers the viewer list and accurate view counts.
> **Done when:** Opening the same story twice as one user records exactly one row in `story_views`, and the author can read back the viewer list.
- [ ] Add Flyway migration for `story_views (story_id, viewer_id, viewed_at)` with a unique constraint on `(story_id, viewer_id)`
- [ ] Add `RecordStoryViewUseCase` + `GetStoryViewersUseCase` in-ports and implement idempotent insert (`ON CONFLICT DO NOTHING`) in the persistence adapter
- [ ] Expose `POST /api/v1/stories/{id}/view` and `GET /api/v1/stories/{id}/viewers` on `StoryController`

### TASK-17.4 — Expiry job + read-time filter
> **Why:** A scheduled cleanup keeps the table small, but a read-time filter is the real safety net so an expired story is never served even if the job is late.
> **Done when:** A story older than 24h is excluded from `GET /api/v1/stories/feed` immediately, and the scheduled job deletes it within its next run.
- [ ] Add a `@Scheduled` `StoryExpiryJob` in `infrastructure/` that hard-deletes rows where `expires_at < now()`
- [ ] Filter all story queries by `expires_at > now()` in `StoryJpaRepository`
- [ ] Add `GetActiveStoriesUseCase` returning per-followed-user active stories

### TASK-17.5 — `reels` model + video transcoding worker (FFmpeg → HLS)
> **Why:** Phones upload one large video, but smooth playback needs multiple bitrate renditions plus an HLS manifest — work too heavy to do in the request.
> **Done when:** Uploading a reel produces an HLS `.m3u8` manifest plus rendition segments in storage, and the reel's status flips from `PROCESSING` to `READY` asynchronously.
- [ ] Add Flyway migration for `reels` (`id`, `user_id`, `caption`, `source_url`, `hls_manifest_url`, `status`, `created_at`)
- [ ] Add `Reel` domain model + `ReelStatus` enum, `ReelRepository` out-port, and `CreateReelUseCase` in-port
- [ ] Add a `VideoTranscoder` out-port and a `FfmpegTranscoderAdapter` (`adapter/out/`) invoking FFmpeg to emit HLS renditions
- [ ] Add an async worker (`@Async` or queue consumer) that transcodes, uploads renditions, then updates `status = READY`
- [ ] Add `ReelController` + `CreateReelRequest` / `ReelResponse` DTOs

### TASK-17.6 — Reels feed (vertical, autoplay) on the frontend
> **Why:** Reels need a full-screen, vertically-snapping, autoplay-one-at-a-time experience distinct from the photo feed.
> **Done when:** Scrolling the reels page snaps each video to full screen and only the in-view reel plays its HLS stream.
- [ ] Add `src/types/reel.ts` (`Reel`, `ReelStatus`) and `src/types/story.ts`
- [ ] Add `src/api/reelsApi.ts` and `src/api/storiesApi.ts` using the shared Axios instance
- [ ] Add `src/hooks/reel/useReelsFeed.ts` (`useInfiniteQuery`) and `src/hooks/story/useStories.ts`
- [ ] Add `src/components/reels/ReelPlayer.tsx` (HLS playback + `IntersectionObserver` autoplay) and `src/components/stories/StoryTray.tsx` / `StoryViewer.tsx`
- [ ] Add `src/pages/reels/ReelsPage.tsx` with CSS scroll-snap and register `/reels` in `src/App.tsx`

### TASK-17.7 — Tests + view-count accuracy
> **Why:** TTL filtering, idempotent views, and async status transitions are exactly the corners where bugs hide, so they need explicit coverage.
> **Done when:** `mvn verify` passes new tests proving expired stories are filtered, duplicate views count once, and a reel reaches `READY` after transcoding.
- [ ] Unit-test `StoryService` (expiry filter, idempotent view) and `ReelService` (status transition) with JUnit 5 + Mockito
- [ ] Integration-test `StoryPersistenceAdapterIT` covering the `(story_id, viewer_id)` unique constraint and active-story query
- [ ] Add a frontend test for `ReelPlayer` autoplay/pause on intersection (Vitest + Testing Library)
