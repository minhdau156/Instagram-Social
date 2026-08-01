# N+1 Query Problem

## What is it
The N+1 query problem is a performance anti-pattern in ORM/database access (e.g., JPA/Hibernate) where fetching a list of N parent entities triggers 1 query for the parents, then N additional queries — one per parent — to lazily load each parent's related child entities. Instead of 1 efficient query (often with a JOIN), the app ends up executing N+1 round-trips to the database.

Example: loading 100 `User` entities, then accessing `user.getPosts()` for each in a loop, triggers 1 query for the users + 100 separate queries for posts = 101 queries total.

## Why it happens
- **Lazy loading** (`FetchType.LAZY`) defers loading associations until they're accessed — if accessed inside a loop, each access fires its own query.
- **No eager fetch/join** specified when the code knows upfront it needs the related data for every row.
- **Iterating over a collection and touching a lazy association per item** without batching or joining.
- Common in JPA/Hibernate, but applies to any ORM (ActiveRecord, Sequelize, TypeORM, etc.).

## How to detect and fix it
1. **Detect**: Enable SQL logging (`spring.jpa.show-sql=true`, or better, `p6spy`/Hibernate statistics) and count queries per request; a request that logs N+1 near-identical `SELECT ... WHERE id = ?` queries is the signature.
2. **Fix options**:
   - **`JOIN FETCH`** in JPQL: `SELECT u FROM User u JOIN FETCH u.posts` — pulls parent + children in one query.
   - **`@EntityGraph`**: declarative way to specify which associations to eagerly fetch for a given query method.
   - **Batch fetching**: `@BatchSize(size = N)` (Hibernate) or `hibernate.default_batch_fetch_size` — turns N queries into ceil(N/batchSize) queries using `IN (...)`.
   - **DTO projections**: query only the exact fields needed via a constructor expression or interface projection, avoiding entity graph traversal entirely.
   - Avoid defaulting everything to `FetchType.EAGER` — that just moves the problem (always joins even when unneeded) and can cause its own performance/duplication issues with multiple collections.

## When it shows up in real life
- REST APIs that return a list of parents with nested children (e.g., `GET /users` where each user's posts/comments/followers are serialized).
- Admin dashboards or reports iterating over a collection and calling a getter per row.
- GraphQL resolvers — very prone to N+1 since each field resolver can independently hit the DB (commonly solved with DataLoader batching).
- Feed/timeline features (e.g., Instagram-style feeds) where each post needs its author, likes count, and comments — a classic N+1 trap if not fetched with joins/batches upfront.
