# TASK-9.19 — Flyway migration: RBAC schema & seed

## Overview

Introduce the database foundation for Role-Based Access Control. Today the schema has **no role concept at all** — `users` only has an `account_status` enum, and the `"role"` claim that `JwtTokenProvider` writes into tokens is never read back into authorities. This migration creates four tables — `roles`, `permissions`, `role_permissions`, and `user_roles` — seeds the four system roles and their default permissions, assigns the `USER` role to every existing user, and bootstraps the first `SUPER_ADMIN`.

This is the **source-of-truth** change for the whole RBAC track. Every later task (domain models in [TASK-9.20](TASK-9.20-rbac-domain-models.md), JPA entities in [TASK-9.25](TASK-9.25-rbac-jpa-entities-repositories.md)) must match the exact column names and types defined here.

---

## Concepts

- **RBAC (Role-Based Access Control)** — users are granted *roles*; roles bundle *permissions*; authorization checks ask "does this user have permission X?" rather than "is this user person Y?".
- **Many-to-many via join tables** — a user can hold multiple roles, a role can be held by many users (`user_roles`); a role can grant many permissions, a permission can belong to many roles (`role_permissions`).
- **System roles** — the four built-in roles (`USER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN`) are flagged `is_system = TRUE` so management endpoints can refuse to delete them.

---

## File Locations

```
backend/src/main/resources/db/migration/V4__add_rbac_tables.sql   ← create
```

> The three existing migrations are `V1__initial_schema.sql`, `V2__seed_data.sql`, `V3__add_fts_indexes.sql`. Confirm `V4` is still the next free version before committing — Flyway fails on a gap or a duplicate version.

---

## Checklist

### Tables

- [ ] `roles` — `id UUID PK DEFAULT uuid_generate_v4()`, `name VARCHAR(50) NOT NULL UNIQUE`, `description TEXT`, `is_system BOOLEAN NOT NULL DEFAULT TRUE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- [ ] `permissions` — `id UUID PK`, `name VARCHAR(100) NOT NULL UNIQUE`, `description TEXT`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
- [ ] `role_permissions` — `role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE`, `permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE`, `PRIMARY KEY (role_id, permission_id)`.
- [ ] `user_roles` — `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE`, `assigned_by UUID REFERENCES users(id)`, `assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `PRIMARY KEY (user_id, role_id)`.
- [ ] Index `idx_user_roles_user ON user_roles(user_id)` — the authority lookup in [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md) filters by `user_id` on every request.

### Seed — roles

- [ ] Insert the four system roles: `USER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN` (all `is_system = TRUE`).

### Seed — permissions

- [ ] Insert the canonical permission set (this list is the contract used by [TASK-9.20](TASK-9.20-rbac-domain-models.md) and [TASK-9.28](TASK-9.28-securityconfig-method-security.md)):
  - `REPORT_VIEW`, `REPORT_REVIEW`
  - `CONTENT_MODERATE`
  - `USER_VIEW`, `USER_SUSPEND`, `USER_UNSUSPEND`
  - `AUDIT_LOG_VIEW`
  - `ROLE_VIEW`, `ROLE_ASSIGN`, `ROLE_PERMISSION_MANAGE`

### Seed — role → permission mapping

- [ ] `USER` → (none of the above; a normal user holds no admin/moderation permissions).
- [ ] `MODERATOR` → `REPORT_VIEW`, `REPORT_REVIEW`, `CONTENT_MODERATE`, `USER_VIEW`.
- [ ] `ADMIN` → all `MODERATOR` permissions **plus** `USER_SUSPEND`, `USER_UNSUSPEND`, `AUDIT_LOG_VIEW`, `ROLE_VIEW`, `ROLE_ASSIGN`.
- [ ] `SUPER_ADMIN` → all `ADMIN` permissions **plus** `ROLE_PERMISSION_MANAGE`.
- [ ] Write the mapping as `INSERT ... SELECT` joins on `roles.name` / `permissions.name` so it does not depend on the generated UUIDs.

### Seed — assignments

- [ ] Assign the `USER` role to every existing row in `users` (`INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE r.name = 'USER'`).
- [ ] Bootstrap one `SUPER_ADMIN`: assign the `SUPER_ADMIN` role to a single known account. Do **not** hardcode a production email in the migration — instead document that the local/dev super-admin is the seed user from `V2__seed_data.sql`, and that production bootstrapping happens via a separate, environment-specific step. (See Notes.)

---

## Notes

- **New users default to `USER`.** Assigning the default role on signup is handled in the application layer ([TASK-9.24](TASK-9.24-rbac-domain-service.md)), not by a DB trigger — keep business rules out of the schema, consistent with the rest of this project.
- **Why `name` is `VARCHAR`, not a PG enum.** The existing schema uses PG enums for fixed sets (`account_status`, etc.), but roles/permissions are *managed at runtime* in this design — admins can change which permissions a role grants. A `VARCHAR(50) UNIQUE` keeps the door open for adding permissions without an `ALTER TYPE` migration. The domain layer still constrains role *names* to a fixed enum ([TASK-9.20](TASK-9.20-rbac-domain-models.md)).
- **Super-admin bootstrap in production.** Never bake a real admin's identity into version-controlled SQL. Document the chosen approach in an ADR ([TASK-10.43](../phase-10/TASK-10.43-first-adr.md)): e.g., a one-off `flyway` callback reading an env var, or a manual `INSERT` run during first deploy.
- **Idempotency.** Flyway runs a versioned migration exactly once, so plain `INSERT`s are fine. If you want the file re-runnable in local resets, use `INSERT ... ON CONFLICT (name) DO NOTHING`.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you write the SQL.

### Concepts to learn
- **RBAC model (roles, permissions, assignments)** — the canonical NIST description — https://csrc.nist.gov/projects/role-based-access-control
- **Many-to-many with join tables** — modelling user↔role and role↔permission — https://www.postgresql.org/docs/current/tutorial-fk.html
- **ON DELETE CASCADE** — auto-clean join rows when a user/role is deleted — https://www.postgresql.org/docs/current/ddl-constraints.html

### Official docs (code reference)
- **Flyway versioned migrations** — naming, ordering, and execution — https://documentation.red-gate.com/fd/migrations-184127470.html
- **PostgreSQL CREATE TABLE** — https://www.postgresql.org/docs/current/sql-createtable.html
