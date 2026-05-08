# Phase 5 — Feed & Discovery

> **Depends on:** Phase 3 (Follow Graph), Phase 4 (Interactions)
> **Blocks:** Phase 10 (caching layer)
> **BRD refs:** FR-009, FR-010
> **DB tables:** `follows`, `user_interests`, `hashtag_stats`
> **Branch prefix:** `feat/phase-5-`

---

## Task Index

### Backend — Home Feed

| Task | Title | Status |
|------|-------|--------|
| [TASK-5.1](TASK-5.1-out-port-feed-repository.md) | Out-port: FeedRepository | ⬜ |
| [TASK-5.2](TASK-5.2-in-ports-feed.md) | In-ports: GetHomeFeedUseCase & GetExploreFeedUseCase | ⬜ |
| [TASK-5.3](TASK-5.3-domain-service-feed.md) | Domain service: FeedService | ⬜ |
| [TASK-5.4](TASK-5.4-jpa-feed-query-adapter.md) | JPA adapter: FeedJpaQueryAdapter | ⬜ |
| [TASK-5.5](TASK-5.5-rest-controller-feed.md) | REST controller: FeedController | ⬜ |
| [TASK-5.6](TASK-5.6-dtos-feed.md) | DTOs: FeedPageResponse & TrendingHashtagResponse | ⬜ |
| [TASK-5.7](TASK-5.7-user-interests-tracking.md) | user_interests tracking | ⬜ |
| [TASK-5.8](TASK-5.8-hashtag-stats.md) | hashtag_stats scheduled rollup | ⬜ |
| [TASK-5.9](TASK-5.9-tests-feed.md) | Unit & integration tests: Feed | ⬜ |

### Frontend

| Task | Title | Status |
|------|-------|--------|
| [TASK-5.10](TASK-5.10-typescript-types.md) | TypeScript types: FeedPage & TrendingHashtag | ⬜ |
| [TASK-5.11](TASK-5.11-api-services-feed.md) | API service: feedApi.ts | ⬜ |
| [TASK-5.12](TASK-5.12-custom-hooks-feed.md) | Custom hooks: useHomeFeed & useExploreFeed | ⬜ |
| [TASK-5.13](TASK-5.13-infinite-scroll.md) | InfiniteScroll utility component | ⬜ |
| [TASK-5.14](TASK-5.14-home-page.md) | Home page: HomePage.tsx | ⬜ |
| [TASK-5.15](TASK-5.15-explore-page.md) | Explore page: ExplorePage.tsx | ⬜ |
| [TASK-5.16](TASK-5.16-post-skeleton.md) | Post skeleton: PostSkeleton.tsx | ⬜ |
| [TASK-5.17](TASK-5.17-register-routes.md) | Register routes: `/` and `/explore` | ⬜ |

---

## Recommended Implementation Order

```
Backend:
5.1 → 5.2 → 5.3          (domain layer)
5.4                        (persistence layer)
5.5 → 5.6                 (web layer)
5.7 → 5.8                 (supporting infra)
5.9                        (tests)

Frontend:
5.10 → 5.11 → 5.12        (data layer)
5.16 → 5.13               (shared UI primitives — skeleton before scroll)
5.14 → 5.15               (pages)
5.17                       (routing)
```
