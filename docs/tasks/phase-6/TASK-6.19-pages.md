# TASK-6.19 — Pages: InboxPage, ChatPage, NewConversationDialog, GroupChatDialog

## Overview

Create the four main messaging UI components. `InboxPage` is the route-level entry point; `ChatPage` renders the open conversation; the two dialogs handle conversation creation.

## Requirements

- Pages live in `frontend/src/pages/messaging/`.
- Dialog components live in `frontend/src/components/messaging/`.
- Protected routes — redirect to `/login` if unauthenticated.
- Responsive: split-panel on desktop (`md+`), full-screen on mobile.
- Use MUI components and `sx` for all styling.

## File Locations

```
frontend/src/pages/messaging/InboxPage.tsx
frontend/src/pages/messaging/ChatPage.tsx
frontend/src/components/messaging/NewConversationDialog.tsx
frontend/src/components/messaging/GroupChatDialog.tsx
```

---

## Instructions

### `InboxPage.tsx`

**Purpose:** Inbox with a conversation list on the left and the active chat on the right (desktop), or just the list (mobile, with navigation to `/messages/:conversationId`).

**Layout:**
- Root: MUI `Box` with `display: 'flex'`, `height: 'calc(100vh - 64px)'` (subtract app bar height), `overflow: 'hidden'`.
- Left panel (conversation list):
  - MUI `Box` with `width: { xs: '100%', md: 360 }`, `borderRight: 1`, `borderColor: 'divider'`, `overflow: 'auto'`.
  - Header: MUI `Box` with `p: 2`, `display: 'flex'`, `justifyContent: 'space-between'`, `alignItems: 'center'`.
    - Title: `Typography variant="h6"` — "Messages".
    - Action buttons: `IconButton` with `EditIcon` (new direct message) and `GroupAddIcon` (new group) from `@mui/icons-material`. Clicking each opens the corresponding dialog.
  - MUI `Divider`.
  - MUI `List` populated by `useConversations()` — render one `ConversationListItem` per conversation.
  - Loading state: render 5 `Skeleton` items (`variant="rectangular"`, `height={72}`, `sx={{ mb: 1 }}`).
  - Empty state: MUI `Box` centered with `Typography` — "No conversations yet".
- Right panel (active chat):
  - Hide on mobile: `display: { xs: 'none', md: 'flex' }`, `flex: 1`.
  - If no conversation is selected: show centered MUI `Typography color="text.secondary"` — "Select a conversation to start chatting".
  - If a conversation is selected: render `<ChatPage conversationId={selectedId} />` inline (not navigating away on desktop).
- On mobile: clicking a `ConversationListItem` navigates to `/messages/:conversationId` using `useNavigate`.

**State:**
- `selectedConversationId: string | null` — local `useState`, updated when a list item is clicked.

---

### `ChatPage.tsx`

**Purpose:** Renders the full message thread for one conversation. Can be used inline inside `InboxPage` (desktop) or as a standalone route (mobile).

**Props (when used inline on desktop):**
- `conversationId: string`
- `onBack?: () => void` — back button shown on mobile

**Layout:**
- Root: MUI `Box` with `display: 'flex'`, `flexDirection: 'column'`, `height: '100%'`.
- **Header bar:** MUI `Box` with `display: 'flex'`, `alignItems: 'center'`, `px: 2`, `py: 1`, `borderBottom: 1`, `borderColor: 'divider'`.
  - MUI `IconButton` with `ArrowBackIcon` — only visible on mobile (`display: { md: 'none' }`), calls `onBack`.
  - MUI `Avatar` (small, 36×36).
  - MUI `Typography variant="subtitle1" fontWeight={600}` — conversation name or other user's username.
  - Spacer: `Box flex={1}`.
  - MUI `IconButton` with `InfoOutlinedIcon` — placeholder for conversation info panel (future feature).
- **Message list area:** MUI `Box` with `flex: 1`, `overflow: 'auto'`, `p: 2`, `display: 'flex'`, `flexDirection: 'column-reverse'` (newest message at bottom, auto-scroll behaviour). Assign a `ref` for programmatic scroll control.
  - Render messages using `MessageBubble` for each item (data from `useMessages`).
  - "Load older messages" trigger at the top: use the same `IntersectionObserver` pattern from `InfiniteScroll`, but the sentinel is at the **top** of the list, and scrolling upward triggers `fetchNextPage`.
  - Loading older messages spinner at the top: `CircularProgress size={24}` in a centered `Box`.
  - `TypingIndicator` component at the bottom of the message list (above the input bar).
- **Input bar:** MUI `Box` with `display: 'flex'`, `alignItems: 'center'`, `px: 2`, `py: 1`, `borderTop: 1`, `borderColor: 'divider'`, `gap: 1`.
  - MUI `IconButton` with `EmojiEmotionsOutlinedIcon` — emoji picker trigger (use `emoji-mart` or `@emoji-mart/react` if installed, or leave as a stub).
  - MUI `IconButton` with `AttachFileIcon` — file/image upload trigger.
  - MUI `TextField` with `multiline`, `maxRows={4}`, `fullWidth`, `size="small"`, `placeholder="Message..."`, `variant="outlined"`, `sx={{ borderRadius: 4 }}`.
    - On `Shift+Enter`: insert newline. On `Enter` alone: submit.
  - MUI `IconButton` with `SendIcon` from `@mui/icons-material`, `color="primary"`. Disabled when input is empty or `isPending` is true.

**Behaviour:**
- On mount: call `messagingApi.markRead(conversationId)` and invalidate `['conversations']`.
- Auto-scroll to bottom on new incoming message: use `useEffect` watching the latest message's `id`.
- Send message: call `sendMessage({ content, messageType: 'TEXT' })` from `useSendMessage`, then clear the input.
- Typing indicator: call `sendTyping(conversationId, true)` from `useWebSocket` on input `onChange`; call `sendTyping(conversationId, false)` on blur or after a 2-second debounce of no input.

---

### `NewConversationDialog.tsx`

**Props:**
- `open: boolean`
- `onClose: () => void`

**Purpose:** Search for a user and start a 1-to-1 conversation with them.

**Layout:**
- MUI `Dialog` with `fullWidth`, `maxWidth="xs"`.
- `DialogTitle`: "New Message".
- `DialogContent`:
  - MUI `TextField` with `fullWidth`, `autoFocus`, `placeholder="Search people..."`, `variant="outlined"`, `size="small"`, `sx={{ mb: 2 }}`.
  - User search results: MUI `List`. Each result is a `ListItemButton` with:
    - `ListItemAvatar` → `Avatar`.
    - `ListItemText primary={username}`.
  - Loading state: `CircularProgress` centered.
  - Empty state: `Typography color="text.secondary"` — "No users found".
- `DialogActions`:
  - MUI `Button variant="text"` — "Cancel" → calls `onClose`.
  - MUI `Button variant="contained"` — "Start Chat" — disabled until a user is selected.

**Behaviour:**
- Use `useQuery` with `['userSearch', searchTerm]` and a debounced search term (300ms) — call `usersApi.search(debouncedTerm)` (same API used in the follow search feature).
- On "Start Chat": call `messagingApi.createConversation({ participantIds: [selectedUserId], isGroup: false })`, then navigate to the new conversation and call `onClose`.
- Invalidate `['conversations']` after creating.

---

### `GroupChatDialog.tsx`

**Props:**
- `open: boolean`
- `onClose: () => void`

**Purpose:** Select multiple users and a group name to create a group conversation.

**Layout:**
- MUI `Dialog` with `fullWidth`, `maxWidth="sm"`.
- `DialogTitle`: "New Group".
- `DialogContent`:
  - Group name input: MUI `TextField` `fullWidth`, `label="Group name"`, `variant="outlined"`, `sx={{ mb: 2 }}`.
  - Selected members chips: MUI `Box` with `display: 'flex'`, `flexWrap: 'wrap'`, `gap: 0.5`, `mb: 1` — render one `Chip` with `avatar` and `onDelete` per selected user.
  - User search input: same as `NewConversationDialog` — `TextField` + results `List`.
  - Clicking a result adds the user to the selected list (and removes them from results to prevent duplicates).
- `DialogActions`:
  - MUI `Button variant="text"` — "Cancel".
  - MUI `Button variant="contained"` — "Create Group" — disabled when fewer than 2 participants or group name is empty.

**Behaviour:**
- Maintain `selectedUsers: User[]` in local state.
- On "Create Group": call `messagingApi.createConversation({ participantIds: selectedUsers.map(u => u.id), name: groupName, isGroup: true })`, navigate, call `onClose`.
- Invalidate `['conversations']` after creating.

## Notes

- `ChatPage` uses `flexDirection: 'column-reverse'` on the message list so that the newest message is always at the bottom without needing `scrollTo` hacks. New messages added to the top of the array (newest-first from the API) will appear at the bottom visually.
- The emoji picker is optional scope — render the `IconButton` but it can open a simple `Popover` saying "Coming soon" if `emoji-mart` is not installed.
- Both dialogs share the user-search logic — consider extracting a `UserSearchList` internal sub-component if the duplication feels excessive.
