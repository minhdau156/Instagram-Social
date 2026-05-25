# Phase 9 — Content Moderation & Admin

> **Depends on:** Phase 1 (Auth/Users), Phase 2 (Posts/Hashtags), Phase 4 (Interactions)
> **BRD refs:** NFR-007, Admin User Stories
> **DB tables:** `reports`, `user_blocks`, `audit_logs`, `roles`, `permissions`, `role_permissions`, `user_roles`
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

### Backend — Authorization & RBAC

> Full role/permission access control. Note: [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md) closes the current gap where the JWT `role` claim is written but never converted into authorities — it is a prerequisite for the `hasRole("ADMIN")` checks the moderation/admin tasks (9.5, 9.9) already assume.

| Task | Title | Status |
|------|-------|--------|
| [TASK-9.19](TASK-9.19-rbac-flyway-migration.md) | Flyway migration: RBAC schema & seed (`roles`, `permissions`, `role_permissions`, `user_roles`) | ⬜ |
| [TASK-9.20](TASK-9.20-rbac-domain-models.md) | Domain models: Role, Permission, RoleName/PermissionName + User extension | ⬜ |
| [TASK-9.21](TASK-9.21-rbac-domain-exceptions.md) | Domain exceptions (RBAC) | ⬜ |
| [TASK-9.22](TASK-9.22-rbac-out-ports.md) | Out-ports: RoleRepository, PermissionRepository | ⬜ |
| [TASK-9.23](TASK-9.23-rbac-in-ports.md) | In-ports: RBAC use-case interfaces | ⬜ |
| [TASK-9.24](TASK-9.24-rbac-domain-service.md) | Domain service: RbacService (privilege & last-super-admin guards) | ⬜ |
| [TASK-9.25](TASK-9.25-rbac-jpa-entities-repositories.md) | JPA entities & repositories (RBAC) | ⬜ |
| [TASK-9.26](TASK-9.26-rbac-persistence-adapter.md) | Persistence adapter: RbacPersistenceAdapter | ⬜ |
| [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md) | Authorization wiring: JWT authorities & auth filter | ⬜ |
| [TASK-9.28](TASK-9.28-securityconfig-method-security.md) | SecurityConfig & method-level `@PreAuthorize` | ⬜ |
| [TASK-9.29](TASK-9.29-rbac-rest-controllers-dtos.md) | REST controllers & DTOs: RoleAdminController | ⬜ |
| [TASK-9.30](TASK-9.30-rbac-tests.md) | Tests (RBAC & authorization, incl. deny paths) | ⬜ |

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

### Frontend — Authorization & RBAC

| Task | Title | Status |
|------|-------|--------|
| [TASK-9.31](TASK-9.31-rbac-types-api-services.md) | TypeScript types & API services: rbac.ts, rbacApi.ts | ⬜ |
| [TASK-9.32](TASK-9.32-frontend-authorization.md) | Authorization primitives: usePermissions, PermissionGate, SuperAdminRoute | ⬜ |
| [TASK-9.33](TASK-9.33-rbac-admin-pages-routes.md) | Admin RBAC pages & route registration | ⬜ |

---

## Recommended Implementation Order

```
Backend — Moderation & Admin:
9.1 → 9.2            (domain layer — models + exceptions)
9.3 → 9.4            (ports)
9.5                  (services)
9.6 → 9.7            (persistence layer)
9.8                  (block-filter integration into Feed & Search)
9.9 → 9.10           (web layer — controllers + DTOs)
9.11                 (tests)

Backend — Authorization & RBAC (do 9.27 before the admin endpoints can actually enforce access):
9.19                 (Flyway migration — schema + seed)
9.20 → 9.21          (domain models + exceptions)
9.22 → 9.23          (ports)
9.24                 (RbacService — guards + audit)
9.25 → 9.26          (persistence layer)
9.27 → 9.28          (authorities wiring + SecurityConfig/@PreAuthorize)  ← unblocks hasRole/hasAuthority everywhere
9.29                 (role-management controller + DTOs)
9.30                 (tests — emphasise deny paths)

Frontend — Moderation & Admin:
9.12 → 9.13          (data contracts + API services)
9.14 → 9.15          (user-facing components + pages)
9.16                 (admin panel pages)
9.17 → 9.18          (route guard + route registration)

Frontend — Authorization & RBAC:
9.31                 (types + API services)
9.32                 (usePermissions, PermissionGate, SuperAdminRoute; make AdminRoute permission-aware)
9.33                 (role-management pages + routes)
```

> **Cross-cutting:** RBAC underpins the admin/moderation enforcement. If building both tracks together, land **9.19–9.28** before wiring `@PreAuthorize`/`hasRole` into the moderation tasks (9.5, 9.9), since those checks are no-ops until the authority pipeline exists.
