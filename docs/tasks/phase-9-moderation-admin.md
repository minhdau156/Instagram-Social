# Phase 9 — Content Moderation & Admin

> **Depends on:** Phase 1, Phase 2, Phase 4  
> **BRD refs:** NFR-007, Admin User Stories  
> **DB tables:** `reports`, `user_blocks`, `audit_logs`  
> **Branch prefix:** `feat/phase-9-`

---

## Backend

### TASK-9.1 — Domain models
- [ ] Create `backend/.../domain/model/Report.java`
  - Fields: `id`, `reporterId`, `entityType` (`POST`/`COMMENT`/`USER`), `entityId`, `reason`, `description`, `status` (`PENDING`/`REVIEWED`/`RESOLVED`/`DISMISSED`), `createdAt`, `resolvedAt`
  - Business method: `withResolved(String resolution)`, `withDismissed()`
- [ ] Create `backend/.../domain/model/UserBlock.java`
  - Fields: `id`, `blockerId`, `blockedId`, `createdAt`

### TASK-9.2 — Domain exceptions
- [ ] Create `backend/.../domain/exception/ReportNotFoundException.java`
- [ ] Create `backend/.../domain/exception/AlreadyBlockedException.java`
- [ ] Create `backend/.../domain/exception/NotBlockedException.java`

### TASK-9.3 — Out-ports
- [ ] `ModerationRepository.java`
  - `saveReport(Report)`, `findReportById(UUID)`, `findPendingReports(Pageable)`
  - `saveBlock(UserBlock)`, `deleteBlock(UUID blockerId, UUID blockedId)`
  - `isBlocked(UUID blockerId, UUID blockedId) → boolean`
  - `findBlockedUsersByBlockerId(UUID, Pageable)`
- [ ] `AuditLogRepository.java`
  - `log(UUID actorId, String action, String entityType, UUID entityId, String detail)`

### TASK-9.4 — In-ports
- [ ] `ReportContentUseCase.java` — `Command(reporterId, entityType, entityId, reason, description?)`
- [ ] `BlockUserUseCase.java` — `Command(blockerId, targetUsername)`
- [ ] `UnblockUserUseCase.java` — `Command(blockerId, targetUsername)`
- [ ] `GetBlockedUsersUseCase.java` — `Query(userId, page, size)`
- [ ] `ReviewReportUseCase.java` — `Command(adminId, reportId, action: RESOLVE|DISMISS, resolutionNote?)`
- [ ] `SuspendUserUseCase.java` — `Command(adminId, targetUserId, reason)`
- [ ] `UnsuspendUserUseCase.java` — `Command(adminId, targetUserId)`
- [ ] `AdminGetReportsUseCase.java` — `Query(status?, page, size)`

### TASK-9.5 — Domain services
- [ ] Create `backend/.../domain/service/ModerationService.java`
  - Implements user-facing in-ports: `ReportContentUseCase`, `BlockUserUseCase`, `UnblockUserUseCase`, `GetBlockedUsersUseCase`
  - Logging: calls `AuditLogRepository.log(...)` for every report/block action
- [ ] Create `backend/.../domain/service/AdminService.java`
  - Implements admin in-ports: `ReviewReportUseCase`, `SuspendUserUseCase`, `UnsuspendUserUseCase`, `AdminGetReportsUseCase`
  - Suspend: updates `User.status = SUSPENDED`; writes audit log; notifies user (email/notification)
  - All methods validate that caller has `ROLE_ADMIN`

### TASK-9.6 — JPA entities & repositories
- [ ] `ReportJpaEntity.java` — `@Table(name = "reports")`
- [ ] `ReportJpaRepository.java` — `findByStatusOrderByCreatedAtDesc(ReportStatus, Pageable)`
- [ ] `UserBlockJpaEntity.java` — `@Table(name = "user_blocks")`, composite PK (`blocker_id`, `blocked_id`)
- [ ] `UserBlockJpaRepository.java` — `existsByBlockerIdAndBlockedId`, `deleteByBlockerIdAndBlockedId`, `findByBlockerIdOrderByCreatedAtDesc`
- [ ] `AuditLogJpaEntity.java` — `@Table(name = "audit_logs")`: `id`, `actorId`, `action`, `entityType`, `entityId`, `detail`, `createdAt`
- [ ] `AuditLogJpaRepository.java`

### TASK-9.7 — Persistence adapters
- [ ] `ModerationPersistenceAdapter.java` — `implements ModerationRepository`
- [ ] `AuditLogPersistenceAdapter.java` — `implements AuditLogRepository`

### TASK-9.8 — Block filtering
- [ ] Update `FeedJpaQueryAdapter` to exclude posts from users who have blocked the current user or been blocked by them
- [ ] Update `SearchJpaAdapter` to exclude blocked users from search results
- [ ] Add utility `BlockFilter.java` in `infrastructure/` — loads blocked user IDs for the current user from cache/DB

### TASK-9.9 — REST controllers
- [ ] Create `ModerationController.java`
  - `POST /api/v1/reports`
  - `POST /api/v1/users/{username}/block`
  - `DELETE /api/v1/users/{username}/block`
  - `GET /api/v1/users/me/blocked`
- [ ] Create `AdminController.java` — secured with `@PreAuthorize("hasRole('ADMIN')")`
  - `GET /api/v1/admin/reports`
  - `PUT /api/v1/admin/reports/{id}`
  - `PUT /api/v1/admin/users/{id}/suspend`
  - `PUT /api/v1/admin/users/{id}/unsuspend`
  - `GET /api/v1/admin/users` — user list with filters

### TASK-9.10 — DTOs
- [ ] `ReportRequest.java` — `entityType`, `entityId`, `reason` (`@NotBlank`), `description?`
- [ ] `ReportResponse.java` — all report fields + reporter username
- [ ] `ReviewReportRequest.java` — `action` (`RESOLVE`/`DISMISS`), `resolutionNote?`
- [ ] `SuspendUserRequest.java` — `reason`

### TASK-9.11 — Tests
- [ ] `ModerationServiceTest.java` — report, block, double-block guard (Mockito)
- [ ] `AdminServiceTest.java` — review report, suspend user, audit log written (Mockito)
- [ ] `ModerationPersistenceAdapterIT.java` — `@DataJpaTest`
- [ ] `ModerationControllerTest.java`, `AdminControllerTest.java` — MockMvc with role-based access checks

---

## Frontend

### TASK-9.12 — TypeScript types
- [ ] Create `frontend/src/types/moderation.ts`
  - `Report`, `ReportReason`, `UserBlock`, `ReportStatus`, `AuditLog`

### TASK-9.13 — API services
- [ ] Create `frontend/src/api/moderationApi.ts`
  - `submitReport(payload)`, `blockUser(username)`, `unblockUser(username)`, `getBlockedUsers()`
- [ ] Create `frontend/src/api/adminApi.ts`
  - `getReports(status?, page)`, `reviewReport(id, payload)`, `suspendUser(id, reason)`, `unsuspendUser(id)`, `getUsers(filters)`

### TASK-9.14 — User-facing components
- [ ] Create `frontend/src/components/moderation/ReportDialog.tsx`
  - MUI Dialog with `RadioGroup` for report reason
  - Optional description textarea
  - Triggered from kebab menus on posts, comments, and user profiles
- [ ] Create `frontend/src/components/moderation/BlockButton.tsx`
  - Shown in user profile kebab menu
  - Calls `blockUser` / `unblockUser` with confirmation dialog

### TASK-9.15 — User-facing pages
- [ ] Create `frontend/src/pages/settings/BlockedAccountsPage.tsx`
  - List of blocked users with `UserListItem` + Unblock button

### TASK-9.16 — Admin panel pages (route prefix `/admin`)
- [ ] Create `frontend/src/pages/admin/AdminDashboardPage.tsx`
  - Basic stats cards: total users, total posts, pending reports
- [ ] Create `frontend/src/pages/admin/AdminReportsPage.tsx`
  - Table of reports, filterable by status (`PENDING`/`REVIEWED`/`RESOLVED`/`DISMISSED`)
  - Per-row actions: Resolve / Dismiss (opens `ReviewReportDialog`)
- [ ] Create `frontend/src/pages/admin/AdminUsersPage.tsx`
  - Table of users with filters (status, username search)
  - Per-row actions: View Profile, Suspend, Unsuspend
- [ ] Create `frontend/src/components/admin/ReviewReportDialog.tsx` — resolution note textarea + action buttons

### TASK-9.17 — Admin route guard
- [ ] Create `frontend/src/components/common/AdminRoute.tsx`
  - Redirects non-admin users to `/` with a toast error

### TASK-9.18 — Register routes
- [ ] Add `/settings/blocked` → `BlockedAccountsPage` (protected)
- [ ] Add `/admin` → `AdminDashboardPage` (AdminRoute)
- [ ] Add `/admin/reports` → `AdminReportsPage` (AdminRoute)
- [ ] Add `/admin/users` → `AdminUsersPage` (AdminRoute)

---

## Authorization & RBAC (Phase 9 extension)

> Full role/permission access control. Detailed per-task files live in [`phase-9/`](phase-9/) as TASK-9.19–9.33.
> **New DB tables:** `roles`, `permissions`, `role_permissions`, `user_roles`.
> **Roles:** `USER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN`.
> **Permissions:** `REPORT_VIEW`, `REPORT_REVIEW`, `CONTENT_MODERATE`, `USER_VIEW`, `USER_SUSPEND`, `USER_UNSUSPEND`, `AUDIT_LOG_VIEW`, `ROLE_VIEW`, `ROLE_ASSIGN`, `ROLE_PERMISSION_MANAGE`.
> **Known gap this closes:** `JwtTokenProvider` writes a `role` claim, but `JwtAuthenticationFilter` builds authorities from `Collections.emptyList()` — so `hasRole('ADMIN')` never passes today. TASK-9.27 fixes this.

### Backend

### TASK-9.19 — Flyway migration: RBAC schema & seed
- [ ] Create `V4__add_rbac_tables.sql` (confirm V4 is the next free version)
- [ ] Tables: `roles`, `permissions`, `role_permissions` (PK role_id+permission_id), `user_roles` (PK user_id+role_id, `assigned_by`, `assigned_at`) + `idx_user_roles_user`
- [ ] Seed 4 system roles, the 10 permissions, role→permission mapping (MODERATOR⊂ADMIN⊂SUPER_ADMIN); assign `USER` to all existing users; bootstrap one `SUPER_ADMIN`

### TASK-9.20 — Domain models: Role, Permission & User extension
- [ ] `RoleName`, `PermissionName` enums; `Permission`, `Role` (with `grants(PermissionName)`)
- [ ] Extend `User` with `Set<Role> roles` + `hasRole`, `hasPermission`, `permissionNames()` (pure Java)

### TASK-9.21 — Domain exceptions (RBAC)
- [ ] `RoleNotFoundException` (404), `RoleAlreadyAssignedException` (409), `RoleNotAssignedException`, `InsufficientPrivilegeException` (403), `ProtectedRoleException` (409) + `GlobalExceptionHandler` mappings + `AccessDeniedException` → 403 JSON

### TASK-9.22 — Out-ports: RoleRepository, PermissionRepository
- [ ] `RoleRepository` (findByName/Id, findRolesByUserId, findPermissionNamesByUserId, assign/revoke, countUsersWithRole, replaceRolePermissions)
- [ ] `PermissionRepository` (findAll, findByName, findByIds)

### TASK-9.23 — In-ports: RBAC use cases
- [ ] `AssignRoleToUserUseCase`, `RevokeRoleFromUserUseCase`, `GetUserRolesUseCase`, `GetUserPermissionsUseCase`, `ListRolesUseCase`, `UpdateRolePermissionsUseCase`, `AssignDefaultRoleUseCase`

### TASK-9.24 — Domain service: RbacService
- [ ] Implements all RBAC in-ports; privilege-escalation guard (only SUPER_ADMIN grants ADMIN/SUPER_ADMIN); last-super-admin guard; protect `ROLE_PERMISSION_MANAGE` on SUPER_ADMIN; audit every mutation via `AuditLogRepository`

### TASK-9.25 — JPA entities & repositories (RBAC)
- [ ] `RoleJpaEntity` (@ManyToMany permissions), `PermissionJpaEntity`, `UserRoleJpaEntity` (composite PK)
- [ ] Repos with `@EntityGraph` for role+permissions and a one-query projection for permission names by user

### TASK-9.26 — Persistence adapter: RbacPersistenceAdapter
- [ ] `implements RoleRepository, PermissionRepository`; private `toDomain` mappers; enum `valueOf` at the boundary; no JPA entity escapes

### TASK-9.27 — Authorization wiring: JWT authorities & auth filter
- [ ] Load roles+permissions per request (DB-backed, Approach A) → `ROLE_<NAME>` + bare permission `GrantedAuthority`s in `JwtAuthenticationFilter` (replaces `Collections.emptyList()`)
- [ ] Keep principal = userId so `currentUserId()` is unchanged; note Redis cache hook for TASK-10.3

### TASK-9.28 — SecurityConfig & method-level authorization
- [ ] `@EnableMethodSecurity`; `requestMatchers("/api/v1/admin/**").hasAnyRole(MODERATOR,ADMIN,SUPER_ADMIN)`; JSON `AccessDeniedHandler`
- [ ] `@PreAuthorize` per action mapped to its permission (REPORT_VIEW/REVIEW, USER_SUSPEND, ROLE_ASSIGN, ROLE_PERMISSION_MANAGE, …)

### TASK-9.29 — REST controllers & DTOs (role management)
- [ ] `RoleAdminController`: GET /admin/roles, GET /admin/permissions, PUT /admin/roles/{name}/permissions, GET/POST /admin/users/{id}/roles, DELETE /admin/users/{id}/roles/{name}, GET /users/me/permissions
- [ ] DTOs: `AssignRoleRequest`, `UpdateRolePermissionsRequest`, `RoleResponse`, `PermissionResponse`, `UserRolesResponse`

### TASK-9.30 — Tests (RBAC & authorization)
- [ ] `RbacServiceTest` (guards + audit), `RbacPersistenceAdapterIT`, `RoleAdminControllerTest`, `AuthorizationIT` — emphasise deny paths (moderator can't suspend; plain user blocked from /admin; admin can't grant super-admin)

### Frontend

### TASK-9.31 — TypeScript types & API services
- [ ] `types/rbac.ts` (`RoleName`, `PermissionName`, `Role`, `Permission`, `UserRoles`, `MyGrants`)
- [ ] `api/rbacApi.ts` (listRoles, listPermissions, updateRolePermissions, getUserRoles, assignRole, revokeRole, getMyGrants)

### TASK-9.32 — Frontend authorization primitives
- [ ] `usePermissions` hook (fetch `/users/me/permissions`, default-deny while loading)
- [ ] `<PermissionGate>`; `<SuperAdminRoute>`; make `AdminRoute` permission-aware (`hasAnyRole`)

### TASK-9.33 — Admin RBAC pages & route registration
- [ ] `RoleManagementPage` (super-admin, permission matrix via `RolePermissionEditor`), `UserRolesPanel` in `AdminUsersPage`
- [ ] Register `/admin/roles` (SuperAdminRoute, lazy + ErrorBoundary) + nav entry
