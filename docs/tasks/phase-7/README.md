# Phase 7 — Notifications

> **Depends on:** Phase 4 (Interactions), Phase 6 (Messaging), Phase 3 (Follow)
> **BRD refs:** FR-0019, FR-0020
> **DB tables:** `notifications`, `notification_settings`, `device_tokens`
> **Branch prefix:** `feat/phase-7-`

---

## Task Index

### Backend

| Task | Title | Status |
|------|-------|--------|
| [TASK-7.1](TASK-7.1-domain-model-notification.md) | Domain model: Notification | ⬜ |
| [TASK-7.2](TASK-7.2-domain-model-notification-settings.md) | Domain model: NotificationSettings | ⬜ |
| [TASK-7.3](TASK-7.3-domain-exceptions.md) | Domain exceptions | ⬜ |
| [TASK-7.4](TASK-7.4-out-ports.md) | Out-ports: NotificationRepository, NotificationSettingsRepository, PushNotificationPort | ⬜ |
| [TASK-7.5](TASK-7.5-in-ports.md) | In-ports: 6 use-case interfaces | ⬜ |
| [TASK-7.6](TASK-7.6-domain-service.md) | Domain service: NotificationService | ⬜ |
| [TASK-7.7](TASK-7.7-domain-event-integration.md) | Domain event integration | ⬜ |
| [TASK-7.8](TASK-7.8-device-token-adapter.md) | Device token JPA & FCM push adapter | ⬜ |
| [TASK-7.9](TASK-7.9-jpa-entities-repositories.md) | JPA entities & repositories | ⬜ |
| [TASK-7.10](TASK-7.10-persistence-adapters.md) | Persistence adapters | ⬜ |
| [TASK-7.11](TASK-7.11-websocket-push.md) | WebSocket push configuration | ⬜ |
| [TASK-7.12](TASK-7.12-rest-controller.md) | REST controller: NotificationController | ⬜ |
| [TASK-7.13](TASK-7.13-dtos.md) | DTOs: Request & Response | ⬜ |
| [TASK-7.14](TASK-7.14-tests.md) | Unit & integration tests | ⬜ |

### Frontend

| Task | Title | Status |
|------|-------|--------|
| [TASK-7.15](TASK-7.15-typescript-types.md) | TypeScript types: notification.ts | ⬜ |
| [TASK-7.16](TASK-7.16-api-services.md) | API service: notificationsApi.ts | ⬜ |
| [TASK-7.17](TASK-7.17-custom-hooks.md) | Custom hooks: useNotifications, useNotificationSettings, useUnreadNotifications | ⬜ |
| [TASK-7.18](TASK-7.18-notification-components.md) | Notification components: NotificationItem, NotificationDropdown, UnreadBadge | ⬜ |
| [TASK-7.19](TASK-7.19-pages.md) | Pages: NotificationsPage, NotificationSettingsPage | ⬜ |
| [TASK-7.20](TASK-7.20-integrate-app-bar.md) | Integrate notification bell into app bar | ⬜ |
| [TASK-7.21](TASK-7.21-register-routes.md) | Register routes: `/notifications` and `/settings/notifications` | ⬜ |

---

## Recommended Implementation Order

```
Backend:
7.1 → 7.2 → 7.3 → 7.4   (domain layer — models, exceptions, out-ports)
7.5                         (in-ports)
7.6                         (domain service)
7.7                         (event wiring into existing services)
7.8 → 7.9 → 7.10          (persistence layer + FCM adapter)
7.11                        (WebSocket config update)
7.12 → 7.13               (web layer — controller + DTOs)
7.14                        (tests)

Frontend:
7.15 → 7.16               (data contracts + API service)
7.17                        (data layer hooks)
7.18                        (shared UI components)
7.19                        (pages)
7.20 → 7.21               (integration into app shell + router)
```
