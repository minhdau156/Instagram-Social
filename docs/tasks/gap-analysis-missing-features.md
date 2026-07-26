# Gap Analysis — Real-World Business Logic Missing from Phases 1–9

> **Purpose:** Phases 1–9 are fully specced and (per the project owner) implemented. This document does **not** compare code against those specs — it asks a different question: *what does a real production Instagram-like product need that no task in phases 1–9 ever asked for?*
>
> Every item below was checked against the actual task files in `docs/tasks/phase-*` (not assumed) — either the concept is entirely absent, or the field/table exists but no task wires it into a real endpoint/flow. Items already planned for later phases (11–20) are called out explicitly so they aren't mistaken for oversights.
>
> Nothing here implies the built phases are wrong — it's the next-layer punch list for "MVP" → "real product."

---

## Phase 1 — Auth & User Management

**Confirmed present (so it's not repeated below):** register/login/logout/refresh, Google/Facebook OAuth2, password-reset-by-email, `isPrivate`/`isVerified`/`status(ACTIVE/DEACTIVATED/SUSPENDED)` fields on `User`, avatar upload, protected routes.

**Missing:**
- **Email verification flow.** `isVerified` exists on `User` and defaults to `false`, but no task ever sends a verification email or exposes a `POST /auth/verify-email` / confirm endpoint — nothing in the spec ever flips it to `true`. Right now every account is permanently unverified.
- **Change password while logged in.** Only `requestReset`/`confirmReset` (forgot-password) exist. There's no "enter current password → set new password" flow for an authenticated user.
- **Change email address.** No flow to update `email` with re-verification.
- **Reactivate a deactivated account.** `withDeactivated()` exists on the domain model, but no corresponding `withReactivated()` / login-time reactivation prompt — once deactivated, a user has no documented way back in.
- **Account lockout / brute-force throttling.** Phase 10 adds generic IP-based rate limiting (bucket4j), but there's no per-account failed-login counter or temporary lockout — someone can hammer one username's password forever from rotating IPs.
- **Session/device management.** `user_sessions` is listed as a phase-1 DB table, but no task actually lists active sessions or lets a user revoke one device's refresh token remotely ("log out of all other devices"). The domain-service note even hedges with "delete from `user_sessions` **if tracked**" — worth confirming refresh tokens are persisted/revocable server-side at all, not just discarded client-side (a stolen refresh token would otherwise stay valid until natural expiry).
- **Username change.** No endpoint to change `username` post-registration (with unique-check).
- **Live username-availability check.** No `GET /auth/check-username` for real-time signup-form feedback.
- **Two-factor authentication (TOTP/SMS).** Not present anywhere.
- **Cross-cutting enforcement of `isPrivate`.** The flag exists on `User`, but worth explicitly verifying it's actually *checked* everywhere content is returned (feed, search, explore, direct profile URL) — it's easy for a private flag to exist on the model but leak data through one query path that forgot to filter on it.

---

## Phase 2 — Posts & Media

**Confirmed present:** carousel posts (multiple `PostMedia` rows with `orderIndex`), image/video/reel media types, caption+location editing (`PUT /posts/{id}`), hashtags, mentions table, MinIO presigned uploads.

**Missing:**
- **Draft / scheduled posts.** `Post.status` only supports active/deleted-style states — no `DRAFT` or `SCHEDULED` status, no "publish later" flow.
- **Archive a post.** No `ARCHIVED` status — a user can only keep a post visible or soft-delete it, not hide it from their own grid while keeping it.
- **Disable comments per post.** No `commentsEnabled`/`allowComments` field or check — post owners can't turn off commenting the way real Instagram allows.
- **Alt text for images.** `PostMedia` has no `altText` field — no accessibility description support.
- **Media re-ordering after upload / edit existing carousel.** Update only covers caption+location; there's no task for reordering or swapping a carousel item post-publish.
- **NSFW/automated content screening on upload.** Nothing runs against uploaded media before it's public (this could reasonably be deferred to Phase 9, but Phase 9 doesn't cover it either — see below).

---

## Phase 3 — Social Graph (Follow/Unfollow)

**Confirmed present:** follow/unfollow, private-account follow **requests** (approve/deny via a dedicated requests page), `user_stats` counters.

**Missing:**
- **Follow suggestions ("Suggested for you" / People You May Know).** No task computes or serves suggestions based on mutual follows, contacts, or interests.
- **Mutual-connections display.** No "Followed by X, Y and 3 others" surfaced anywhere.
- **Remove a follower.** Only unfollow (stop following someone) exists — there's no "remove this person from my followers" action for the profile owner.
- **Close Friends list.** No concept of a favorites/close-friends subset of followers (relevant later for Stories in Phase 17, but the underlying follow-graph feature has no hook for it yet).
- **Search/filter within followers/following lists.** No task addresses searching a large followers list.

---

## Phase 4 — Social Interactions (Likes, Comments, Saves, Shares)

**Confirmed present:** post & comment likes, **threaded comment replies** (`parentId`, `GetRepliesUseCase`, "View N replies" UI — this is fully covered, not a gap), saves, shares.

**Missing:**
- **Saved-post collections/albums.** Saves are a flat list (`SavedPost`) — no named collections to organize saved posts into folders the way Instagram does.
- **Pin a comment.** Post owners can't pin a top comment.
- **Comment reporting/moderation hook.** Likes/comments have no link into Phase 9's report flow specifically for individual comments (Phase 9 covers reporting posts/users generically — worth confirming comment-level reporting is wired, not just post-level).
- **Like/comment notification batching.** ("Alice and 12 others liked your post") — related to the Phase 7 gap below; the like/comment side never produces a "grouped" event, so Phase 7 has nothing to batch even if it wanted to.
- **Un-share / remove a share.** Share creation exists; no task removes/undoes a share.

---

## Phase 5 — Feed & Discovery

**Confirmed present:** home feed (plain reverse-chronological `ORDER BY created_at DESC`), explore feed (engagement-score + trending-hashtag ordering), `user_interests` tracking, infinite scroll.

**Missing (some intentionally deferred — noted):**
- **Personalized/ranked home feed.** Home feed is strictly chronological, not relevance-ranked. This is *intentional* — Phase 18 ("Recommendations & Ranking") is the dedicated later phase for this — but worth knowing it's not in scope of 1–9 at all, not a bug.
- **"Not interested" / hide-this-post feedback.** No signal a user can give to suppress similar content.
- **Mute a followed account's posts** (stay following, stop seeing their posts in feed) — distinct from unfollow; not present.
- **Seen-post de-duplication.** No tracking of which feed items a user has already scrolled past, so a refreshed feed can re-show the same posts.
- **Feed fan-out-on-write / precomputation.** Feed is computed on read via query; fan-out-on-write is explicitly Phase 11/12 territory (event-driven + CQRS) — fine for now, just not in 1–9.

---

## Phase 6 — Direct Messaging

**Confirmed present:** conversations (1:1 + group), messages, read receipts (`message_reads`), typing indicator, WebSocket real-time delivery, unread badge.

**Missing:**
- **Message requests.** Real Instagram routes DMs from non-followed/non-mutual accounts into a separate "Requests" inbox the recipient must accept. No task implements this — every message goes straight into the primary inbox regardless of relationship.
- **Unsend / delete a sent message.** No task removes or recalls a message after sending.
- **Edit a sent message.** Not present.
- **Message reactions (emoji react to a single message).** Not present.
- **Media/file attachments in a message.** Check whether `Message` supports anything beyond plain text — no task explicitly adds an image/video attachment field or upload flow for DMs.
- **Mute a conversation.** No per-conversation notification mute.
- **Group chat admin controls.** No task covers renaming a group, removing a member, or leaving a group conversation (only creation via `GroupChatDialog` is covered).
- **Block-aware messaging.** No explicit check that a blocked user (Phase 9) can't message you — worth verifying `user_blocks` is actually consulted by `MessagingService`.

---

## Phase 7 — Notifications

**Confirmed present:** notification domain + settings, device tokens + FCM push, WebSocket real-time push, notification settings page.

**Missing:**
- **Notification grouping/batching.** No task aggregates multiple like/follow events into a single grouped notification ("Alice and 12 others liked your post") — every event is delivered as its own row.
- **Mark-all-as-read.** Only individual read-state is implied; no bulk "clear all" action.
- **Email digest notifications** (daily/weekly summary email). Not present — push/in-app/WebSocket only.
- **Do-not-disturb / quiet hours.** `NotificationSettings` covers per-category toggles but no time-window muting.
- **Mention notifications.** Given Phase 2 has a `mentions` table, confirm a notification actually fires when someone is `@mentioned` in a caption or comment — it's not called out as its own event type in the phase-7 task list explicitly.

---

## Phase 8 — Search

**Confirmed present:** user/hashtag/post search, full-text search (Postgres FTS), search history **with clear/delete support**, recent-searches UI.

**Missing:**
- **Search-result type filters.** No explicit "People / Tags / Places" tab filtering in the API or UI — search appears to return a blended result set without a filterable type facet.
- **Location/place search.** Since Phase 2 added a `location` field on posts, there's no companion "search by place" capability.
- **Typeahead ranking by relationship.** Results aren't weighted by mutual followers/relationship strength — purely text-match relevance.

---

## Phase 9 — Content Moderation & Admin

**Confirmed present:** user-submitted reports, user blocks (with feed/search filtering), full RBAC (roles/permissions), audit logging, admin dashboard/reports/users pages.

**Missing:**
- **Audit log viewer.** `audit_logs` are written on every admin mutation, but the admin panel task list (dashboard, reports, users) never adds a page to actually *view* them — the data is captured but operators have no UI to inspect it.
- **Automated/proactive content moderation.** Every moderation action is reactive (a user must file a report) — no keyword filter, image-hash matching, or ML-based auto-flagging of new posts/comments.
- **Appeals process.** A suspended/actioned user has no documented way to contest the decision.
- **Temporary suspensions with auto-expiry.** `AdminService` suspend/unsuspend reads as a binary flip — no suspension-duration field or scheduled auto-unsuspend.
- **Shadowban / reduced-visibility enforcement.** Only two states exist for a bad actor: report-and-review or hard-suspend — there's no lighter-touch "limit this account's reach" tool.
- **Bulk moderation actions.** No batch-approve/dismiss across multiple reports at once — each is handled one at a time.

---

## Summary table

| Phase | Biggest real-world gaps |
|---|---|
| 1 — Auth | Email verification, session/device management, change-password/email, 2FA, account lockout |
| 2 — Posts | Drafts/scheduling, archive, disable-comments, alt text |
| 3 — Social Graph | Follow suggestions, remove-follower, close friends |
| 4 — Interactions | Saved-post collections, comment pinning, like/comment batching signal |
| 5 — Feed | Personalized ranking (by design → Phase 18), "not interested", mute-without-unfollow |
| 6 — Messaging | Message requests inbox, unsend/edit message, media attachments, mute conversation |
| 7 — Notifications | Grouped/batched notifications, mark-all-read, email digests, DND hours |
| 8 — Search | Result-type filters, place search, relationship-weighted ranking |
| 9 — Moderation | Audit log viewer UI, proactive/automated moderation, appeals, temporary suspensions |
