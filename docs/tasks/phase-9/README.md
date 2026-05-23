# Phase 9 — Content Moderation & Admin

> **Depends on:** Phase 1 (Auth/Users), Phase 2 (Posts/Hashtags), Phase 4 (Interactions)
> **BRD refs:** NFR-007, Admin User Stories
> **DB tables:** `reports`, `user_blocks`, `audit_logs`
> **Branch prefix:** `feat/phase-9-`

---

## Task Index

### Backend

| Task | Title | Status |
|------|-------|--------|
| [TASK-9.1](TASK-9.1-domain-models.md) | Domain models: Report, UserBlock | ⬜ |
| [TASK-9.2](TASK-9.2-domain-exceptions.md) | Domain exceptions | ⬜ |
| [TASK-9.3](TASK-9.3-out-ports.md) | Out-ports: ModerationRepository, AuditLogRepository | ⬜ |
| [TASK-9.4](TASK-9.4-in-ports.md) | In-ports: 8 use-case interfaces | ⬜ |
| [TASK-9.5](TASK-9.5-domain-services.md) | Domain services: ModerationService, AdminService | ⬜ |
| [TASK-9.6](TASK-9.6-jpa-entities-repositories.md) | JPA entities & repositories | ⬜ |
| [TASK-9.7](TASK-9.7-persistence-adapters.md) | Persistence adapters | ⬜ |
| [TASK-9.8](TASK-9.8-block-filtering.md) | Block filtering: Feed & Search integration | ⬜ |
| [TASK-9.9](TASK-9.9-rest-controllers.md) | REST controllers: ModerationController, AdminController | ⬜ |
| [TASK-9.10](TASK-9.10-dtos.md) | DTOs: Request & Response | ⬜ |
| [TASK-9.11](TASK-9.11-tests.md) | Unit & integration tests | ⬜ |

### Frontend

| Task | Title | Status |
|------|-------|--------|
| [TASK-9.12](TASK-9.12-typescript-types.md) | TypeScript types: moderation.ts | ⬜ |
| [TASK-9.13](TASK-9.13-api-services.md) | API services: moderationApi.ts, adminApi.ts | ⬜ |
| [TASK-9.14](TASK-9.14-user-facing-components.md) | User-facing components: ReportDialog, BlockButton | ⬜ |
| [TASK-9.15](TASK-9.15-user-facing-pages.md) | User-facing pages: BlockedAccountsPage | ⬜ |
| [TASK-9.16](TASK-9.16-admin-panel-pages.md) | Admin panel pages: Dashboard, Reports, Users | ⬜ |
| [TASK-9.17](TASK-9.17-admin-route-guard.md) | Admin route guard: AdminRoute | ⬜ |
| [TASK-9.18](TASK-9.18-register-routes.md) | Register routes | ⬜ |

---

## Recommended Implementation Order

```
Backend:
9.1 → 9.2            (domain layer — models + exceptions)
9.3 → 9.4            (ports)
9.5                  (services)
9.6 → 9.7            (persistence layer)
9.8                  (block-filter integration into Feed & Search)
9.9 → 9.10           (web layer — controllers + DTOs)
9.11                 (tests)

Frontend:
9.12 → 9.13          (data contracts + API services)
9.14 → 9.15          (user-facing components + pages)
9.16                 (admin panel pages)
9.17 → 9.18          (route guard + route registration)
```
