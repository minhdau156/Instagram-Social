# TASK-7.20 — Integrate Notification Bell into App Bar

## Overview

Add the `UnreadBadge` bell icon to the app bar in `AppShell.tsx` and wire it to open `NotificationDropdown`. Requires managing the popover anchor state inside `AppShell`.

## Requirements

- Modify `AppShell.tsx` only.
- The bell icon appears in the right section of the app bar alongside other icons.

## File Location

```
frontend/src/AppShell.tsx    (modify)
```

---

## Instructions

**Step 1 — Add anchor state for the popover:**
Declare a `useState` variable typed as `HTMLButtonElement | null`, initialised to `null`. This variable serves as the "is the dropdown open?" signal: when it is `null` the dropdown is closed; when it holds the bell button DOM element the dropdown is open. Using the DOM element itself (rather than a separate boolean) is the MUI `Popover` pattern — the element is both the open signal and the position anchor.

**Step 2 — Get the live unread count:**
Call `useUnreadNotifications()` and read `unreadCount` from it. Place this alongside the other hook calls at the top of the component.

**Step 3 — Render `UnreadBadge` in the app bar:**
Find the section of the app bar JSX where other right-side icon buttons are (such as the messages icon from phase 6). Add `UnreadBadge` next to them. Pass `unreadCount` and an `onClick` handler. In the `onClick` handler, set the anchor state to `e.currentTarget` — the button element that was clicked.

**Why `e.currentTarget` and not `e.target`:**
`e.target` is whichever element the user's pointer was directly over when they clicked — this could be the SVG icon, the SVG path, or any nested element inside the button. `e.currentTarget` is always the element the event handler is attached to, which is the `IconButton` itself. The `Popover` needs the outer button element as its anchor so it can position itself correctly; using `e.target` would randomly break positioning whenever the click lands on a child element.

**Step 4 — Render `NotificationDropdown`:**
Place `NotificationDropdown` at the bottom of the component's JSX return, outside the app bar element, as a sibling. Pass the anchor state as `anchorEl` and a function that sets the anchor state back to `null` as `onClose`. The `Popover` inside `NotificationDropdown` reads `open` from `Boolean(anchorEl)`, so when the anchor becomes `null` the popover closes automatically.

**Step 5 — Add imports:**
Import `UnreadBadge` from `'./components/notifications/UnreadBadge'`, `NotificationDropdown` from `'./components/notifications/NotificationDropdown'`, and `useUnreadNotifications` from `'./hooks/useUnreadNotifications'`. Add `useState` to the React import if it is not already there.

## Notes

- After wiring, test: clicking the bell opens the dropdown; clicking outside or pressing Escape closes it; the badge count disappears after "Mark all as read".
- Do not put the `Popover` directly in `AppShell` — it belongs inside `NotificationDropdown`. `AppShell` only manages the anchor element state.
