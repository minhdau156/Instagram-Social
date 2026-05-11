# Phase 6 — Direct Messaging

> **Depends on:** Phase 1 (Auth & Users), Phase 2 (Posts & Media)
> **Blocks:** Phase 7 (message notifications)
> **BRD refs:** FR-0017, FR-0018
> **DB tables:** `conversations`, `conversation_members`, `messages`, `message_reads`
> **Branch prefix:** `feat/phase-6-`

---

## Task Index

### Backend

| Task | Title | Status |
|------|-------|--------|
| [TASK-6.1](TASK-6.1-domain-models.md) | Domain models: Conversation, ConversationMember, Message | ⬜ |
| [TASK-6.2](TASK-6.2-domain-exceptions.md) | Domain exceptions | ⬜ |
| [TASK-6.3](TASK-6.3-out-ports.md) | Out-ports: ConversationRepository & MessageRepository | ⬜ |
| [TASK-6.4](TASK-6.4-in-ports.md) | In-ports: 7 use-case interfaces | ⬜ |
| [TASK-6.5](TASK-6.5-domain-service.md) | Domain service: MessagingService | ⬜ |
| [TASK-6.6](TASK-6.6-jpa-entities.md) | JPA entities | ⬜ |
| [TASK-6.7](TASK-6.7-jpa-repositories.md) | JPA repositories | ⬜ |
| [TASK-6.8](TASK-6.8-persistence-adapters.md) | Persistence adapters | ⬜ |
| [TASK-6.9](TASK-6.9-websocket-config.md) | WebSocket configuration | ⬜ |
| [TASK-6.10](TASK-6.10-websocket-controller.md) | WebSocket controller | ⬜ |
| [TASK-6.11](TASK-6.11-rest-controller.md) | REST controller: MessageController | ⬜ |
| [TASK-6.12](TASK-6.12-dtos.md) | DTOs: Request & Response | ⬜ |
| [TASK-6.13](TASK-6.13-tests.md) | Unit & integration tests | ⬜ |

### Frontend

| Task | Title | Status |
|------|-------|--------|
| [TASK-6.14](TASK-6.14-typescript-types.md) | TypeScript types: messaging.ts | ⬜ |
| [TASK-6.15](TASK-6.15-api-services.md) | API service: messagingApi.ts | ⬜ |
| [TASK-6.16](TASK-6.16-websocket-hook.md) | WebSocket hook: useWebSocket.ts | ⬜ |
| [TASK-6.17](TASK-6.17-custom-hooks.md) | Custom hooks: useConversations, useMessages, useSendMessage | ⬜ |
| [TASK-6.18](TASK-6.18-message-components.md) | Message components: MessageBubble, ConversationListItem, TypingIndicator | ⬜ |
| [TASK-6.19](TASK-6.19-pages.md) | Pages: InboxPage, ChatPage, NewConversationDialog, GroupChatDialog | ⬜ |
| [TASK-6.20](TASK-6.20-unread-badge.md) | Unread badge in navigation | ⬜ |
| [TASK-6.21](TASK-6.21-register-routes.md) | Register routes: `/messages` and `/messages/:conversationId` | ⬜ |

---

## Recommended Implementation Order

```
Backend:
6.1 → 6.2 → 6.3 → 6.4       (domain layer)
6.5                            (domain service)
6.6 → 6.7 → 6.8              (persistence layer)
6.9 → 6.10 → 6.11 → 6.12    (web + WebSocket layer)
6.13                           (tests)

Frontend:
6.14 → 6.15                   (data contracts)
6.16 → 6.17                   (data layer hooks)
6.18                           (shared UI primitives)
6.19                           (pages)
6.20 → 6.21                   (integration)
```
