# TASK-8.10 — API Service: searchApi.ts

## Overview

Create the Axios-based API service for the search feature. Follow the exact same pattern as `notificationsApi.ts` and `messagingApi.ts` — one file per domain, all functions async, all return typed data, no direct `fetch` calls.

## Requirements

- Use the shared `api` Axios instance from `'./client'` — it already attaches the JWT `Authorization` header via interceptor.
- Never call `fetch` directly.
- All functions typed using the interfaces from `search.ts`.

## File Location

```
frontend/src/api/searchApi.ts
```

---

## Checklist

- [ ] Import `api` from `'./client'`.
- [ ] Import `UserSearchResult`, `HashtagSearchResult`, `PostSearchResult`, `SearchHistoryItem`, `SearchType` from `'../types/search'`.

- [ ] Export `searchApi` object with the following methods:

### `searchUsers(q: string, page = 0, size = 20): Promise<UserSearchResult[]>`

- `GET /api/v1/search?q={q}&type=users&page={page}&size={size}`
- Unwrap with `.then(r => r.data.data)` — the backend wraps all responses in `{ data: [...], error: null }`.

### `searchHashtags(q: string, page = 0, size = 20): Promise<HashtagSearchResult[]>`

- `GET /api/v1/search?q={q}&type=hashtags&page={page}&size={size}`
- Unwrap with `.then(r => r.data.data)`.

### `searchPosts(q: string, page = 0, size = 20): Promise<PostSearchResult[]>`

- `GET /api/v1/search?q={q}&type=posts&page={page}&size={size}`
- Unwrap with `.then(r => r.data.data)`.

### `getSearchHistory(): Promise<SearchHistoryItem[]>`

- `GET /api/v1/search/history`
- Unwrap with `.then(r => r.data.data)`.

### `clearSearchHistory(): Promise<void>`

- `DELETE /api/v1/search/history`
- No response body — return the promise directly without unwrapping.

### `getPostsByHashtag(hashtagName: string, page = 0, size = 20): Promise<PostSearchResult[]>`

- `GET /api/v1/hashtags/{hashtagName}/posts?page={page}&size={size}`
- Unwrap with `.then(r => r.data.data)`.
- Note: `hashtagName` is passed WITHOUT the `#` prefix. If the UI strips it before calling this function, document that in a comment here.

## Notes

- The `api` Axios instance returns `AxiosResponse<ApiResponse<T>>` where `ApiResponse<T> = { data: T; error: null }`. Always use `.then(r => r.data.data)` to unwrap the body. For void methods, just return the raw promise.
- There is no `search(q, type, page)` unified function — separate typed functions are easier to consume from hooks without casting. Each hook calls the specific function for its type.
- Do NOT add `searchType` as a parameter to these functions — the naming already encodes the type.
