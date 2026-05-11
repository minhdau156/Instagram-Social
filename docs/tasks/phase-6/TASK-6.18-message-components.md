# TASK-6.18 — Message Components: MessageBubble, ConversationListItem, TypingIndicator

## Overview

Create three reusable messaging UI components. These are shared primitives used by the inbox and chat pages.

## Requirements

- Lives in `frontend/src/components/messaging/`.
- Use MUI components and `sx` prop for all styling — no inline `style={{}}` objects, no hardcoded colours.
- Use theme tokens for colours and spacing.

## File Locations

```
frontend/src/components/messaging/MessageBubble.tsx
frontend/src/components/messaging/ConversationListItem.tsx
frontend/src/components/messaging/TypingIndicator.tsx
```

---

## Instructions

### `MessageBubble.tsx`

**Props:**
- `message: Message`
- `isOwn: boolean` — true when `message.senderId === currentUser.id`

**Layout:**
- Use MUI `Box` as the outer wrapper.
- Align bubbles: `isOwn` → `justifyContent: 'flex-end'`; others → `justifyContent: 'flex-start'`. Use `display: 'flex'` on the wrapper.
- The bubble itself is a `Box` with `sx`:
  - `borderRadius`: `isOwn` → `'18px 18px 4px 18px'`; others → `'18px 18px 18px 4px'`
  - `bgcolor`: `isOwn` → `'primary.main'`; others → `'grey.100'` (light mode) — use `theme.palette`
  - `color`: `isOwn` → `'primary.contrastText'`; others → `'text.primary'`
  - `px: 2, py: 1`
  - `maxWidth: '70%'`

**Content variants by `messageType`:**
- `TEXT`: MUI `Typography` with `variant="body2"` and `message.content`.
- `IMAGE`: MUI `Box` with a `<img>` tag (`src={message.mediaUrl}`), `sx={{ maxWidth: 240, borderRadius: 2, display: 'block' }}`. Show a `Skeleton` while loading.
- `VIDEO`: MUI `Box` with a `<video controls>` element, same sizing as image.
- `POST_SHARE`: MUI `Card` with `variant="outlined"` — show a thumbnail (if available) and a label "Shared post". Keep it compact (`sx={{ width: 200 }}`).

**Status indicator (own messages only):**
- Below the bubble: small MUI `Typography` with `variant="caption"` and `color="text.secondary"`.
- `SENT` → show a single tick icon (`DoneIcon` from `@mui/icons-material`).
- `DELIVERED` → show double tick icon (`DoneAllIcon`).
- `READ` → show double tick icon in `primary.main` colour.

**Timestamp:**
- Show a MUI `Typography` `variant="caption"` `color="text.disabled"` with the formatted time (hour:minute). Place it below the status indicator for own messages, or below the bubble for others.
- Use `new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })`.

---

### `ConversationListItem.tsx`

**Props:**
- `conversation: Conversation`
- `currentUserId: string`
- `isSelected: boolean`
- `onClick: () => void`

**Layout:** Use MUI `ListItemButton` as the root (it handles hover, selected state, and keyboard nav).
- `selected={isSelected}` — MUI highlights it automatically using `action.selected` from the theme.

**Inside the list item:**
- MUI `ListItemAvatar`: `Avatar` with `src={otherMember.avatarUrl ?? undefined}` and fallback initial. For group chats, use a `GroupIcon` from `@mui/icons-material`.
- MUI `ListItemText`:
  - `primary`: conversation display name. For 1-to-1 chats, derive it from the other participant's `senderUsername`. For group chats, use `conversation.name`.
  - `secondary`: `conversation.lastMessage?.content ?? 'No messages yet'`. Truncate with MUI's `noWrap` Typography prop (`sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}`).
- Timestamp: MUI `Typography` `variant="caption"` `color="text.secondary"` aligned to the right — use `sx={{ ml: 'auto', alignSelf: 'flex-start' }}` on a `Box` wrapper.
- Unread badge: MUI `Badge` with `badgeContent={conversation.unreadCount}` and `color="primary"`. Only render it when `unreadCount > 0`. Place it overlapping the avatar.

---

### `TypingIndicator.tsx`

**Props:**
- `typingUsernames: string[]` — list of usernames currently typing (empty when nobody is typing)

**Visibility:** only render when `typingUsernames.length > 0`. Return `null` otherwise.

**Layout:**
- MUI `Box` with `display: 'flex'`, `alignItems: 'center'`, `gap: 1`, `px: 2`, `py: 0.5`.
- Small MUI `Avatar` (size 24×24) for the first typing user (if avatar URL is available; otherwise initial).
- Animated dot bubble: three `Box` elements styled as small circles (`width: 8, height: 8, borderRadius: '50%', bgcolor: 'text.disabled'`).
- Animate the dots with a CSS keyframe animation using MUI's `keyframes` helper from `@mui/system`:
  - Each dot fades in/out with a 0.4s delay offset (0s, 0.2s, 0.4s) to create a staggered bounce effect.
  - Apply via `sx={{ animation: \`${bounceDots} 1s infinite\`, animationDelay: '0s|0.2s|0.4s' }}`.
- Optional text label: `MUI Typography variant="caption" color="text.secondary"` — e.g., `"Anna is typing…"` for one user, or `"2 people are typing…"` for multiple.

## Notes

- Import `Message`, `Conversation`, `MessageType`, `MessageStatus` from `../../types/messaging`.
- Do not add click handlers or routing logic inside `MessageBubble` — it is a pure display component.
- `ConversationListItem` does not call `messagingApi` directly — it receives the `conversation` object as a prop from the parent list.
- `TypingIndicator` receives processed `typingUsernames` from the parent (`ChatPage`) which reads from `useWebSocket`.
