# Current Feature

## Status
Not Started

## Goals
<!-- -->

## Notes
<!-- -->

## History
- TASK-10.27 — Actuator & Micrometer: `spring-boot-starter-actuator` + `micrometer-registry-prometheus` added to pom.xml; `management:` block exposes health/info/metrics/prometheus; SecurityConfig guards `/actuator/**` behind ROLE_ADMIN (health public); `MetricsConfig` declares `postsCreatedCounter` (`posts.published`), `likesAddedCounter`, `usersRegisteredCounter` beans; PostService, LikeService, UserService instrumented; `application-local.yml` fixed to include prometheus in exposure list; counter name changed from `posts.created` to `posts.published` to avoid Micrometer 1.13 stripping the OpenMetrics-reserved `_created` suffix.
- TASK-10.26 — Structured logging & MDC: `MdcLoggingFilter` (@Order(0)) stamps `requestId`, `userId`, `method`, `path` into SLF4J MDC and echoes `X-Request-Id` header; `logback-spring.xml` outputs human-readable logs for `local` profile and JSON via `logstash-logback-encoder:7.4` for all other profiles; `@Slf4j` + `log.info`/`log.debug` added to all 17 controllers and `PostService`.
- TASK-10.25 — Trace one request end-to-end: started backend with local profile, sent `POST /api/v1/posts`, identified Hibernate SQL `insert into posts` on thread `tomcat-handler-6`, confirming the Controller → Service → Persistence adapter call chain with no source changes.
- TASK-10.5 — CDN-backed media URLs: `MinioStorageAdapter` injects `cdnBaseUrl` from `app.minio.cdn-base-url`; `uploadFile` returns CDN-rooted URL; `getPublicUrl(key)` added to adapter and `MediaStoragePort`; `generatePresignedPutUrl` unchanged (targets MinIO directly); `application.yml` adds `cdn-base-url` property (env-var driven, defaults to MinIO endpoint); `application-local.yml` fixes path to `app.minio.cdn-base-url: http://localhost:9000`; `docs/infra/cdn-setup.md` created with local dev, CloudFront/S3, and nginx/MinIO reference configs.
- TASK-10.4 — N+1 fix: removed `@ManyToOne user` from `PostJpaEntity`, replaced with plain `@Column userId`; `FeedJpaQueryAdapter`, `PostPersistenceAdapter`, `SearchJpaAdapter` all use `entity.getUserId()`; `application-local.yml` gains `generate_statistics` + `org.hibernate.stat DEBUG`; `FeedJpaQueryAdapterIT` asserts `PrepareStatementCount < 5` for a 7-post feed.
- TASK-9.33 — `RolePermissionEditor` (checkbox grid + SUPER_ADMIN/ROLE_PERMISSION_MANAGE lock + Tooltip); `RoleManagementPage` (lazy, SuperAdminRoute-gated, one editor per role, `isPending` disables during save); `UserRolesPanel` (chips with ADMIN/SUPER_ADMIN confirm-revoke dialog, add-role Select gated by `ROLE_ASSIGN`, toast on all errors); `AdminUsersPage` gains "Manage roles" menu item → dialog; `/admin/roles` route registered in `App.tsx`; "Roles & Permissions" nav button in `AdminDashboardPage` gated by `PermissionGate role="SUPER_ADMIN"`.
- TASK-9.32 — `usePermissions` hook (`['me','grants']`, 5-min staleTime, default-deny while loading, `hasPermission`/`hasRole`/`hasAnyRole`); `PermissionGate` component (permission/role arrays, `requireAll`, `fallback`); `SuperAdminRoute` (redirects unless `SUPER_ADMIN`); `AdminRoute` updated to `hasAnyRole(['MODERATOR','ADMIN','SUPER_ADMIN'])`.
- TASK-9.31 — `frontend/src/types/rbac.ts` (`RoleName`, `PermissionName` 10-value union, `Permission`, `Role`, `UserRoles`, `MyGrants` interfaces — no `any`); `frontend/src/api/rbacApi.ts` (7 async functions: `listRoles`, `listPermissions`, `updateRolePermissions`, `getUserRoles`, `assignRole`, `revokeRole`, `getMyGrants` — shared `api` Axios instance, `data.data` unwrap).
- TASK-9.30 — `RbacServiceTest` (8 unit tests: assign happy path + ArgumentCaptor audit, privilege-escalation, duplicate-role, last-super-admin, ProtectedRoleException, non-super-admin permissions update, idempotent default role, null-safe empty permissions); `RbacPersistenceAdapterIT` (5 DataJpaTest: findRolesByUserId with permissions, findPermissionNamesByUserId flat/dedup, assign+revoke lifecycle, replaceRolePermissions, countUsersWithRole); `RoleAdminControllerTest` (6 WebMvcTest: ROLE_VIEW/403, ROLE_PERMISSION_MANAGE/403, ROLE_ASSIGN/401); `AuthorizationIT` (5 WebMvcTest + real AdminService/RbacService: MODERATOR review→200/suspend→403, USER→filter-chain-403 with JSON body, ADMIN privilege-escalation→403, no-auth→401). 210/210 tests green.
- TASK-9.29 — `RoleAdminController` (6 endpoints under `/api/v1/admin`: GET/PUT roles, GET/POST/DELETE user roles); DTOs: `AssignRoleRequest`, `UpdateRolePermissionsRequest`, `RoleResponse`, `PermissionResponse`, `UserRolesResponse`, `UserGrantsResponse`; `GET /users/me/permissions` added to `UserController`.
- TASK-9.28 — `RestAccessDeniedHandler` (HTTP 403 + `ApiResponse.error` JSON); `SecurityConfig` broadened admin path guard to `hasAnyRole(MODERATOR,ADMIN,SUPER_ADMIN)` + wired handler; `@PreAuthorize` per permission on `AdminService` (REPORT_VIEW, REPORT_REVIEW, USER_SUSPEND, USER_UNSUSPEND), `AdminController.searchUsers` (USER_VIEW), `RbacService` (ROLE_VIEW, ROLE_ASSIGN, ROLE_PERMISSION_MANAGE).
- TASK-9.27 — `JwtAuthenticationFilter` loads roles → `ROLE_<NAME>` + permissions → bare name from DB per request; `try/catch` ensures authority-load failures leave the request unauthenticated without throwing; removed unused `GetUserPermissionsUseCase` injection; `Collections` import cleaned up.
- TASK-9.26 — RbacPersistenceAdapter implementing RoleRepository + PermissionRepository; `findByUserId` fixed to use JPQL subquery via `UserRoleJpaEntity`; `findAllRoles` uses `findAllWithPermissions()` for N+1 safety; all 13 out-port methods implemented with `toRoleDomain`/`toPermissionDomain` helpers.
- TASK-9.25 — JPA entities & repositories (RBAC): `RoleJpaEntity` (`roles`, `@ManyToMany` → `PermissionJpaEntity` via `role_permissions`, `@EntityGraph`-backed); `PermissionJpaEntity` (`permissions`); `UserRoleJpaEntity` (`user_roles`, `@EmbeddedId UserRoleId`); `RoleJpaRepository` (`findByName` + `findAllWithPermissions` both with `@EntityGraph`); `PermissionJpaRepository` (`findByName`, `findByIdIn`); `UserRoleJpaRepository` (`findByIdUserId`, `existsByIdUserIdAndIdRoleId`, `deleteByUserIdAndRoleId`, `countByIdRoleId`, single-JPQL `findPermissionNamesByUserId`).
- TASK-9.24 — RbacService: 7 RBAC use cases in `application/service/`; privilege-escalation, last-super-admin, and lockout guards; all mutations audited via `AuditLogRepository`; `assignDefaultRole` wired into `UserService.register()`.
- TASK-9.23 — In-ports (RBAC): 7 use-case interfaces in `domain/port/in/rbac/`.
- TASK-9.22 — Out-ports (RBAC): `RoleRepository` (10 methods) and `PermissionRepository` (3 methods) — pure Java, no Spring/JPA imports.
- TASK-9.21 — Domain exceptions (RBAC): 5 exception classes + handlers in `GlobalExceptionHandler`.
- TASK-9.20 — Domain models: `RoleName`, `PermissionName` enums; `Permission`, `Role` domain objects; `User` extended with RBAC fields.
- TASK-9.19 — Flyway migration V4: RBAC tables, 4 system roles, 10 permissions, bulk USER assignment, dev super-admin bootstrap.
- TASK-9.18 — Register Routes: lazy page imports, admin routes, settings dropdown, ReportDialog, BlockButton.
- TASK-9.17 — Admin Route Guard: `AdminRoute` component, `isAdmin` on `AuthContext`.
- TASK-9.16 — Admin Panel Pages: 5 admin hooks, ReviewReportDialog, AdminDashboardPage, AdminReportsPage, AdminUsersPage.
- TASK-9.15 — User-Facing Pages: `useBlockedUsers`, `BlockedAccountsPage`.
- TASK-9.14 — User-Facing Components: `useSubmitReport`, `useBlockUser`, `useUnblockUser`, `ReportDialog`, `BlockButton`.
- TASK-9.13 — API Services: `moderationApi.ts`, `adminApi.ts`.
- TASK-9.12 — TypeScript Types: `moderation.ts`.
- TASK-9.11 — Unit & Integration Tests: ModerationServiceTest, AdminServiceTest, ModerationPersistenceAdapterIT, ModerationControllerIT, AdminControllerIT. 56/56 tests green.
- TASK-9.9 — REST Controllers: ModerationController, AdminController
- TASK-9.10 — DTOs: Request & Response
- TASK-9.8 — Block Filtering
- TASK-9.7 — Persistence Adapters
- TASK-9.6 — JPA Entities & Repositories
- TASK-9.5 — Domain Services: ModerationService, AdminService
- TASK-9.4 — In-Ports: Use-Case Interfaces
- TASK-9.3 — Out-Ports: ModerationRepository, AuditLogRepository
- TASK-9.2 — Domain Exceptions
- TASK-9.1 — Domain Models: Report, UserBlock
- TASK-10.1 — `docs/database/query-baseline.md`: EXPLAIN ANALYZE output for 22 queries; seed SQL (`seed-10k.sql`).
- TASK-10.2 — HikariCP pool tuning: `spring.datasource.hikari` in `application.yml`; local override in `application-local.yml`.
- TASK-10.6 — Frontend image optimization: lazy loading, React.lazy route chunks, rollup-plugin-visualizer.
- TASK-10.3 — Redis caching: 16 named caches with per-cache TTLs across 15 service classes.
- TASK-10.7 — Database index audit: `V5__add_performance_indexes.sql` adds 4 indexes.
- TASK-10.8 — Keyset cursor pagination: `CursorPageResponse<T>` + `CursorEncoder`; native SQL row-comparison queries; frontend infinite scroll hooks.
- TASK-10.9 — HTTP response compression & payload slimming: gzip, ObjectMapper fixes, PostResponse field reduction.
- TASK-10.10 — Async processing with virtual threads: `mediaExecutor` bean, `@Async`, `POST /api/v1/media/process` 202 endpoint.
- TASK-10.11 — Streaming large exports: `GET /api/v1/users/me/export` CSV via StreamingResponseBody.
- TASK-10.12 — Chunked resumable multipart upload: MultipartMinioClient, V6 migration, UploadSession entity, 3 new endpoints.
- TASK-10.13 — Spring Batch bulk post import: importPostsJob, AdminImportController, V7 migration.
- TASK-10.14 — Baseline security headers: X-Content-Type-Options, X-Frame-Options, CSP in SecurityConfig.
- TASK-10.15 — Secrets hygiene: JWT secret fast-fail, .env.example, README setup step.
- TASK-10.16 — JWT hardening: RS256 RSA-2048, key pair loaded via KeyFactory, .env.example updated.
- TASK-10.17 — Rate limiting: bucket4j + caffeine-jcache, 3 filters, 429 with Retry-After header.
- TASK-10.18 — Input validation hardening: `jsoup:1.17.2` replaces AntiSamy (no policy file needed); `HtmlSanitizer` bean uses `Jsoup.clean(Safelist.none())`; `@Size`/`@NotBlank`/`@Pattern`/`@Email` added to all key request DTOs; `htmlSanitizer.sanitize()` called in `PostController` (create + update), `CommentController`, `UserController`, `MessageController`; `GlobalExceptionHandler` confirmed present for `HttpMessageNotReadableException` → 400.
- TASK-10.20 — HTTPS/TLS: `forward-headers-strategy: FRAMEWORK` in `application-prod.yml`; HSTS (`maxAge=31536000`, `includeSubDomains=true`, `preload=false`) in `SecurityConfig`; `docs/infra/tls-setup.md` created with nginx + Let's Encrypt, AWS ALB, and Kubernetes Ingress + cert-manager options.
- TASK-10.21 — IDOR audit: ownership checks verified in `PostService` (update/delete), `CommentService` (edit/delete), `FollowService` (implicit via DB query); all `Unauthorized*AccessException` types confirmed mapped to 403 in `GlobalExceptionHandler`; `PostControllerIT` converted from `@SpringBootTest` → `@WebMvcTest` (fixes H2 DDL failure) and gains `updatePost_nonOwner` + `deletePost_nonOwner` 403 tests; `CommentControllerTest` gains `deleteComment_nonOwner` 403 test; admin routes confirmed gated in `SecurityConfig`.
- TASK-10.22 — Media upload hardening: `MediaValidator` (Tika magic-byte MIME check, 10 MB/100 MB size limits, 8192×8192 dimension guard, JPEG EXIF/GPS stripping, WebP early-return); `InvalidMediaException` → 400; server-side key generation with safe-extension allowlist in `PostService` and `MediaController`; presigned URL expiry 15 min → 5 min; class-level Javadoc on `MediaController` documents presigned-URL gap.
- TASK-10.24 — Idempotency keys: `V8__idempotency_keys.sql` migration; `IdempotencyKeyJpaEntity` + `IdempotencyKeyJpaRepository` (findByKeyAndUserId); `IdempotencyInterceptor` short-circuits duplicate keys (same key+body → cached response, same key+different body → 409); `WebMvcConfig` registers request/response caching filters (order 1/2) and wires interceptor on `/api/v1/posts` and `/api/v1/messages`.
