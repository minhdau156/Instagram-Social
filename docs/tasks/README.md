# Task Index

> Auto-generated from `docs/plan.md` — break-down of all phases into atomic, implementable tasks.
> Each task file maps exactly to one phase and follows the Hexagonal Architecture implementation order defined in `context/ai-interaction.md`.

---

## Implementation Order

```
Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5
                                                ↓
                         Phase 6 → Phase 7 → Phase 8 → Phase 9 → Phase 10
                                                                    ↓
                            Advanced Track ──► Phases 11–20 (see advanced-roadmap.md)
```

---

## Phase Files

| Phase | File | Status | Task Count |
|---|---|---|---|
| Phase 0 — Foundation & Infrastructure | [phase-0-foundation.md](./phase-0-foundation.md) | 🟡 Partially Done | 13 tasks |
| Phase 1 — Authentication & User Management | [phase-1-auth-user-management.md](./phase-1-auth-user-management.md) | ⬜ Not Started | 23 tasks |
| Phase 2 — Posts & Media | [phase-2-posts-media.md](./phase-2-posts-media.md) | 🟡 Partially Done | 22 tasks |
| Phase 3 — Social Graph (Follow/Unfollow) | [phase-3-social-graph.md](./phase-3-social-graph.md) | ⬜ Not Started | 20 tasks |
| Phase 4 — Social Interactions | [phase-4-social-interactions.md](./phase-4-social-interactions.md) | ⬜ Not Started | 35 tasks |
| Phase 5 — Feed & Discovery | [phase-5-feed-discovery.md](./phase-5-feed-discovery.md) | ⬜ Not Started | 17 tasks |
| Phase 6 — Direct Messaging | [phase-6-direct-messaging.md](./phase-6-direct-messaging.md) | ⬜ Not Started | 21 tasks |
| Phase 7 — Notifications | [phase-7-notifications.md](./phase-7-notifications.md) | ⬜ Not Started | 21 tasks |
| Phase 8 — Search | [phase-8-search.md](./phase-8-search.md) | ⬜ Not Started | 15 tasks |
| Phase 9 — Content Moderation & Admin | [phase-9-moderation-admin.md](./phase-9-moderation-admin.md) | ⬜ Not Started | 18 tasks |
| Phase 10 — Performance, Security & Polish | [phase-10-performance-security-polish.md](./phase-10-performance-security-polish.md) | ⬜ Not Started | 52 tasks (33 core + 13 learning + 6 advanced backend) |

---

## Advanced Track (Phases 11–20)

> Optional senior-level phases that extend the project after Phase 10. The shared overview (tracks, suggested order, skills, best practices) lives in **[advanced-roadmap.md](./advanced-roadmap.md)**; each phase below has its own full task file with `Why:` / `Done when:` on every task, matching Phase 10.

| Phase | Track | Focus | Status | Task Count |
|---|---|---|---|---|
| [Phase 11 — Event-Driven Architecture & Async Messaging](./phase-11-event-driven-architecture.md) | 🏛️ Backend | Domain events, transactional outbox, Kafka fan-out | ⬜ Not Started | 8 tasks |
| [Phase 12 — CQRS & Read Models](./phase-12-cqrs-read-models.md) | 🏛️ Backend | Command/query split, projections, eventual consistency | ⬜ Not Started | 7 tasks |
| [Phase 13 — Resilience & Fault Tolerance](./phase-13-resilience-fault-tolerance.md) | 🏛️ Backend | Resilience4j: timeouts, retries, circuit breakers, bulkheads | ⬜ Not Started | 8 tasks |
| [Phase 14 — Kubernetes Deployment](./phase-14-kubernetes-deployment.md) | ☁️ Cloud/SRE | Helm, probes, autoscaling, config/secrets | ⬜ Not Started | 8 tasks |
| [Phase 15 — Infrastructure as Code](./phase-15-infrastructure-as-code.md) | ☁️ Cloud/SRE | Terraform modules, remote state, environments | ⬜ Not Started | 7 tasks |
| [Phase 16 — SRE: SLOs, Alerting & Progressive Delivery](./phase-16-sre-slo-progressive-delivery.md) | ☁️ Cloud/SRE | SLOs, error budgets, canary/blue-green rollback | ⬜ Not Started | 7 tasks |
| [Phase 17 — Stories & Reels](./phase-17-stories-reels.md) | ✨ Product | TTL content, video transcoding (FFmpeg/HLS), view tracking | ⬜ Not Started | 7 tasks |
| [Phase 18 — Recommendations & Ranking Feed](./phase-18-recommendations-ranking.md) | ✨ Product | Candidate→rank pipeline, analytics events, A/B testing | ⬜ Not Started | 7 tasks |
| [Phase 19 — GraphQL API Layer](./phase-19-graphql-api.md) | 🔌 API/FE | Schema-first, DataLoader, cursor pagination | ⬜ Not Started | 8 tasks |
| [Phase 20 — Frontend Excellence](./phase-20-frontend-excellence.md) | 🔌 API/FE | a11y, i18n, PWA/offline, Core Web Vitals | ⬜ Not Started | 8 tasks |

---

## Task Naming Convention

Tasks are named `TASK-<phase>.<sequence>`. When working on a task:

1. Open the relevant phase file.
2. Check off `[ ]` → `[x]` as you complete sub-items.
3. Update `context/current-feature.md` to reflect the active task.

## Per-Task Implementation Order (Backend)

Every backend feature follows this strict order (from `context/ai-interaction.md`):

1. Domain model → `domain/model/`
2. Out-port → `domain/port/out/`
3. In-port(s) → `domain/port/in/`
4. Domain service → `domain/service/`
5. JPA entity → `adapter/out/persistence/`
6. JPA repository → `adapter/out/persistence/`
7. Persistence adapter → `adapter/out/persistence/`
8. Request/Response DTOs → `adapter/in/web/dto/`
9. Controller → `adapter/in/web/`
10. Tests → `test/.../`

## Per-Task Implementation Order (Frontend)

1. TypeScript types → `src/types/`
2. API service → `src/api/`
3. Custom hook → `src/hooks/`
4. Components → `src/components/<feature>/`
5. Page → `src/pages/<feature>/`
6. Route registration → `src/App.tsx`
