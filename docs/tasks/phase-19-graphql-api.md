# Phase 19 — GraphQL API Layer

> **Track:** API/Frontend · **Depends on:** Phases 2-5 · **New tools:** Spring for GraphQL, Apollo Client (frontend)  
> **Branch prefix:** `feat/phase-19-`

---

> **Skills you'll build:**
> - Schema-first GraphQL design
> - Resolvers + DataLoader batching to kill N+1
> - Relay-style cursor pagination
> - Auth inside GraphQL
> - When *not* to use GraphQL (vs. REST/gRPC)
>
> **Best practices:** schema-first; batch with DataLoader; enforce query depth/complexity limits to prevent abuse; use persisted queries in production; keep REST for binary/file uploads.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Backend

### TASK-19.1 — Add Spring for GraphQL + a `.graphqls` schema
> **Why:** A GraphQL endpoint lets clients ask for exactly the fields they need in one round trip instead of stitching together several REST calls.
> **Done when:** GraphiQL loads at `/graphiql` and a trivial `{ __typename }` query returns a result against the running app.
- [ ] Add `spring-boot-starter-graphql` to `backend/pom.xml`
- [ ] Enable GraphiQL in `application.yml` (`spring.graphql.graphiql.enabled=true`)
- [ ] Create `backend/src/main/resources/graphql/schema.graphqls` defining `User`, `Post`, `Media` types and the root `Query`
- [ ] Map a domain enum (e.g. `MediaType`) to a GraphQL `enum`
- [ ] Confirm `/graphiql` loads and the schema tab shows your types

### TASK-19.2 — Query resolvers for user / post / feed
> **Why:** Resolvers are the bridge from a GraphQL field to your existing hexagonal use-cases — they delegate, never hold business logic.
> **Done when:** `user(username:)`, `post(id:)`, and `feed(first:)` queries each return live data fetched through the existing in-port use-cases.
- [ ] Create `PostGraphQlController` in `adapter/in/graphql/` with `@QueryMapping` methods delegating to use-cases
- [ ] Create `UserGraphQlController` with a `@QueryMapping user(@Argument String username)`
- [ ] Map GraphQL types to existing domain models via small mappers (no JPA entities exposed)
- [ ] Verify each query in GraphiQL returns the same data its REST counterpart does

### TASK-19.3 — DataLoader batching for author / media / counts
> **Why:** Resolving a nested field per row fires one query per item — the N+1 explosion; DataLoader collects the keys for a request and loads them in a single batch.
> **Done when:** Querying a feed of N posts with their authors issues a constant number of DB queries (verified via Hibernate statistics), not 1 + N.
- [ ] Register a `BatchLoaderRegistry` loader for authors keyed by `userId`
- [ ] Add `@SchemaMapping` resolvers for `Post.author`, `Post.media`, and `Post.counts` that read from the DataLoader
- [ ] Add a batch repository method (e.g. `findAllByIdIn`) backing each loader
- [ ] Enable Hibernate statistics in a test and assert the query count stays constant as N grows

### TASK-19.4 — Relay-style cursor pagination
> **Why:** Offset paging breaks when rows shift; cursor (Relay Connection) paging is stable and is the GraphQL ecosystem standard.
> **Done when:** `feed(first: 10, after: $cursor)` returns a `Connection` with `edges`, `pageInfo.hasNextPage`, and `endCursor`, and paging forward never repeats or skips a post.
- [ ] Define `PostConnection`, `PostEdge`, and `PageInfo` types in the schema
- [ ] Encode/decode opaque cursors (Base64 of the sort key) in a small helper
- [ ] Wire the feed resolver to accept `first` + `after` and return a connection
- [ ] Verify in GraphiQL that paging with `endCursor` advances without duplicates

### TASK-19.5 — Mutations (createPost, like) + auth
> **Why:** Reads aren't enough — mutations let GraphQL clients write, and they must respect the same authenticated principal as the REST layer.
> **Done when:** `createPost` and `likePost` mutations succeed only for an authenticated user and return `401`/error for an anonymous request.
- [ ] Add `Mutation` type with `createPost(input:)` and `likePost(postId:)` to the schema
- [ ] Implement `@MutationMapping` methods delegating to the existing use-cases
- [ ] Resolve the current user from the security context (reuse the REST `currentUserId()` approach)
- [ ] Annotate mutations with `@PreAuthorize` / verify auth and confirm an anonymous call is rejected

### TASK-19.6 — Query depth/complexity limits
> **Why:** A single deeply nested GraphQL query can recurse into thousands of joins; depth and complexity limits stop a malicious or accidental query from taking down the server.
> **Done when:** A query nested beyond the configured depth limit is rejected with a clear GraphQL error before it ever hits the database.
- [ ] Register a `MaxQueryDepthInstrumentation` and `MaxQueryComplexityInstrumentation` bean
- [ ] Configure sensible limits (e.g. depth 10, complexity 200) in `infrastructure/config/`
- [ ] Verify an over-deep query returns a validation error and does not execute resolvers

## Frontend

### TASK-19.7 — Frontend: Apollo Client + migrate one page
> **Why:** Apollo Client gives the React app a typed GraphQL cache; migrating one page proves the end-to-end path without rewriting everything at once.
> **Done when:** One existing page (e.g. profile or post detail) renders entirely from a single GraphQL query via Apollo, with no REST call for that data.
- [ ] Add `@apollo/client` + `graphql` to `frontend/package.json`
- [ ] Create `src/api/graphqlClient.ts` configuring `ApolloClient` with the JWT auth link
- [ ] Wrap the app (or the target route) in `<ApolloProvider>` in `src/App.tsx`
- [ ] Add the GraphQL query and a `useQuery` hook under `src/hooks/`, then render the chosen page from it
- [ ] Confirm in the Network tab the page loads via one `/graphql` request

## Tests

### TASK-19.8 — Tests + schema snapshot
> **Why:** GraphQL tests catch resolver and schema regressions, and a committed schema snapshot makes any breaking change to the contract show up in a diff.
> **Done when:** `GraphQlTester`-based tests for the feed query and a mutation pass, and the committed `schema.graphqls` snapshot matches the live schema (CI fails on drift).
- [ ] Write `@GraphQlTest` (or `@SpringBootTest` + `GraphQlTester`) tests for `feed`, `user`, and `createPost`
- [ ] Assert DataLoader keeps the query count constant in the feed test
- [ ] Add a test that fails if the live schema diverges from the committed `schema.graphqls` snapshot
- [ ] Run the suite and confirm all GraphQL tests pass green
