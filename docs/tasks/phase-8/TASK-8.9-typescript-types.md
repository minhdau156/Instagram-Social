# TASK-8.9 — TypeScript Types: search.ts

## Overview

Create the TypeScript type definitions for the search feature. These must mirror the backend response DTOs from TASK-8.7 exactly so no manual field conversion is needed when reading API responses.

## Requirements

- No `any` types. Strict TypeScript throughout.
- Use string literal union types (not TypeScript `enum`) for `SearchType` — keeps values directly comparable to API strings without conversion.
- All types exported individually — no default export from this file.

## File Location

```
frontend/src/types/search.ts
```

---

## Checklist

### `SearchType`

- [x] String literal union: `'users' | 'hashtags' | 'posts'`
  - These match the `type` query parameter values accepted by `GET /api/v1/search`.

### `UserSearchResult`

- [x] Interface fields (mirrors `UserSearchResponse` from the backend):
  - `id: string`
  - `username: string`
  - `fullName: string`
  - `avatarUrl: string | null`
  - `isPrivate: boolean`
  - `followerCount: number`

### `HashtagSearchResult`

- [x] Interface fields (mirrors `HashtagSearchResponse`):
  - `id: string`
  - `name: string` — without `#` prefix; the UI adds `#` when rendering
  - `postCount: number`

### `PostSearchResult`

- [x] Interface fields (mirrors `PostSearchResponse`):
  - `id: string`
  - `authorUsername: string`
  - `authorAvatarUrl: string | null`
  - `caption: string | null`
  - `mediaUrl: string`
  - `mediaType: 'IMAGE' | 'VIDEO'`
  - `likeCount: number`
  - `commentCount: number`
  - `createdAt: string` — ISO 8601; parse with `new Date(...)` when formatting

### `SearchHistoryItem`

- [x] Interface fields (mirrors `SearchHistoryResponse`):
  - `id: string`
  - `query: string`
  - `searchedAt: string` — ISO 8601

### `SearchResult` (discriminated union — optional helper type)

- [x] A union type to represent what the search hook may hold depending on `SearchType`:
  ```ts
  export type SearchResult =
    | { type: 'users';    items: UserSearchResult[]    }
    | { type: 'hashtags'; items: HashtagSearchResult[] }
    | { type: 'posts';    items: PostSearchResult[]    };
  ```
  - This is a convenience type for components that receive mixed results. It is optional; the hooks may simply return typed arrays directly instead.

## Notes

- Use `string` for all UUID fields — Axios deserialises them as strings.
- `avatarUrl` and `authorAvatarUrl` are `null` when the user has not uploaded an avatar. The UI must render a fallback letter avatar.
- `caption` is `null | string` — some posts have no text caption (image-only posts).
- `mediaType` is a string literal union rather than a full `MediaType` enum import to keep the types file self-contained.
