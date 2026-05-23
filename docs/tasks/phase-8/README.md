# Phase 8 — Search

> **Depends on:** Phase 1 (Auth/Users), Phase 2 (Posts/Hashtags), Phase 5 (Feed)
> **BRD refs:** FR-0010, FR-0011
> **DB tables:** `hashtags`, `hashtag_stats`, `post_hashtags`, `search_history`, `users`, `posts`
> **Branch prefix:** `feat/phase-8-`
---

## Task Index

### Backend

| Task | Title | Status |
|------|-------|--------|
| [TASK-8.1](TASK-8.1-domain-model-search-history.md) | Domain model: SearchHistory | ✅ |
| [TASK-8.2](TASK-8.2-out-ports.md) | Out-ports: SearchRepository, SearchHistoryRepository | ✅ |
| [TASK-8.3](TASK-8.3-in-ports.md) | In-ports: 6 use-case interfaces | ✅ |
| [TASK-8.4](TASK-8.4-domain-service.md) | Application service: SearchService | ✅ |
| [TASK-8.5](TASK-8.5-jpa-adapter.md) | JPA entities, repositories & persistence adapters | ✅ |
| [TASK-8.6](TASK-8.6-rest-controller.md) | REST controller: SearchController | ✅ |
| [TASK-8.7](TASK-8.7-dtos.md) | DTOs: Request & Response | ✅ |
| [TASK-8.8](TASK-8.8-tests.md) | Unit & integration tests | ✅ |

### Frontend

| Task | Title | Status |
|------|-------|--------|
| [TASK-8.9](TASK-8.9-typescript-types.md) | TypeScript types: search.ts | ✅ |
| [TASK-8.10](TASK-8.10-api-service.md) | API service: searchApi.ts | ✅ |
| [TASK-8.11](TASK-8.11-custom-hooks.md) | Custom hooks: useSearch, useSearchHistory, useHashtagPosts | ✅ |
| [TASK-8.12](TASK-8.12-search-bar-component.md) | Search bar component: SearchBar | ✅ |
| [TASK-8.13](TASK-8.13-recent-searches-component.md) | Recent searches component: RecentSearches | ✅ |
| [TASK-8.14](TASK-8.14-pages.md) | Pages: SearchPage, HashtagPage | ✅ |
| [TASK-8.15](TASK-8.15-register-routes.md) | Register routes & integrate SearchBar into navigation | ✅ |

### Performance

| Task | Title | Status |
|------|-------|--------|
| [TASK-8.16](TASK-8.16-full-text-search.md) | Full-text search integration (FTS indexes + SearchJpaAdapter) | ✅ |

---

## Recommended Implementation Order

```
Backend:
8.1 → 8.2            (domain layer — model + out-ports)
8.3                   (in-ports)
8.4                   (application service)
8.5                   (persistence layer — ILIKE queries, JPA entities, repos, adapters)
8.6 → 8.7            (web layer — controller + DTOs)
8.8                   (tests)

Frontend:
8.9 → 8.10           (data contracts + API service)
8.11                  (data layer hooks)
8.12 → 8.13          (shared UI components)
8.14                  (pages)
8.15                  (integration into app shell + router)

Performance (can run independently after 8.5):
8.16                  (FTS migration + SearchJpaAdapter update + FTS integration tests)
```
