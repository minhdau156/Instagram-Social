# AI Interaction Guidelines

This document defines how to work effectively with AI assistants (Antigravity / Claude) on this project. It documents the expected behavior when generating code, the context files to always reference, and the rules AI must follow.

---

## Context Files — Purpose and Priority

Always load these files in order when starting a new session or task:

| File | Purpose |
|---|---|
| `context/project-overview.md` | Tech stack, architecture diagram, package structure, local dev URLs |
| `context/coding-standard.md` | Naming conventions, patterns, and rules for all generated code |
| `context/current-feature.md` | The specific feature or task currently being worked on |
| `docs/plan.md` | Full phased roadmap — reference for scope and phase ordering |
| `docs/database/schema.sql` | Full DB schema — source of truth for column names and types |

> **Rule:** Before generating any code, the AI must confirm it has read the relevant context files. Never guess at package names, column names, or patterns — always derive them from the actual project files.

---

## Code Generation Rules

### Always Follow These

1. **Match the existing architecture.** Every new feature must follow the Hexagonal Architecture layers: `domain` → `application` → `adapter` → `infrastructure`. Never skip layers.

2. **Use the naming conventions from `coding-standard.md`.** Use-cases are named `<Verb><Noun>UseCase`, repositories are `<Noun>Repository`, JPA entities are `<Noun>JpaEntity`, etc.

3. **Reference the database schema** (`docs/database/schema.sql`) for exact column names, types, constraints, and indexes before writing JPA entities or queries.

4. **Domain layer stays pure.** No Spring, JPA, or Lombok annotations in `domain/model/` or `domain/port/`. Use the `Post.java` entity as a reference implementation.

5. **Never create new patterns** that contradict established ones. If you notice `PostPersistenceAdapter` uses private `toEntity()` / `toDomain()` mapping methods, all new persistence adapters must do the same.

6. **Check existing files first.** Before creating a new file, scan the current package structure to ensure the file doesn't already exist in a different form.

7. **Full implementation, no placeholders.** Don't output `// TODO: implement this` or skeleton methods unless explicitly asked to scaffold. Each generated class must be fully functional.

8. **Validate inputs on the adapter boundary.** Add Bean Validation annotations (`@NotNull`, `@Size`, etc.) on all request DTOs. The domain layer trusts its inputs.

---

## When Adding a New Domain Feature

Follow this exact order — do not jump steps:

```
1. Domain model       → domain/model/<Entity>.java
2. Out-port           → domain/port/out/<Noun>Repository.java
3. In-port(s)         → domain/port/in/<Verb><Noun>UseCase.java  (one file per use case)
4. Domain service     → domain/service/<Noun>Service.java
5. JPA entity         → adapter/out/persistence/<Noun>JpaEntity.java
6. JPA repository     → adapter/out/persistence/<Noun>JpaRepository.java
7. Persistence adapter→ adapter/out/persistence/<Noun>PersistenceAdapter.java
8. Request/Response   → adapter/in/web/dto/<Verb><Noun>Request.java  +  <Noun>Response.java
9. Controller         → adapter/in/web/<Noun>Controller.java
10. Tests             → test/.../domain/<Noun>ServiceTest.java
                        test/.../adapter/<Noun>PersistenceAdapterIT.java
                        test/.../adapter/<Noun>ControllerTest.java
```

---

## When Adding a New Frontend Feature

```
1. TypeScript types    → src/types/<feature>.ts
2. API service         → src/api/<feature>Api.ts
3. Custom hook         → src/hooks/use<Feature>.ts
4. Components          → src/components/<feature>/<ComponentName>.tsx
5. Page                → src/pages/<feature>/<Feature>Page.tsx
6. Route               → Register route in src/App.tsx
```

---

## Response Format Expectations

When the AI generates code, it should:

- **Show the file path** as a comment or header before each code block.
- **Generate complete files**, not fragments, unless the user asks for a specific section.
- **List all changes** at the end of the response (files created, files modified).
- **Flag breaking changes** or schema changes explicitly.
- **Not repeat code** already shown in the same response — reference it by filename instead.

---

## Task Handoff via `current-feature.md`

The file `context/current-feature.md` tracks the active work item. It should always contain:

```markdown
# Current Feature: <Phase X — Feature Name>

## Status
In Progress | Blocked | Review | Done

## Goal
One-sentence description of what is being built.

## Scope
- Backend: list of files to create/modify
- Frontend: list of files to create/modify
- DB: Flyway migration file (if needed)

## Acceptance Criteria  ← success criteria for Goal-Driven Execution (CLAUDE.md §4)
- [ ] Criterion 1
- [ ] Criterion 2

## Notes / Open Questions
- Any blockers, design decisions, or TODOs
```

Update this file at the start of each work session and when switching features.

---

## What the AI Should Never Do

- ❌ Add business logic to controllers — controllers only delegate to use-case interfaces.
- ❌ Expose JPA entities outside the `persistence` package.
- ❌ Add `@Autowired` field injection — always use constructor injection.
- ❌ Use `any` type in TypeScript.
- ❌ Create Flyway migrations that drop or rename existing columns without explicit instruction.
- ❌ Skip writing tests when implementing a complete feature.
- ❌ Put domain exceptions (e.g., `PostNotFoundException`) in the adapter layer.
- ❌ Use `System.out.println()` — use SLF4J (`log.info(...)`, `log.error(...)`).

---

## Useful Reference Files

| What | Where |
|---|---|
| Domain entity pattern | `backend/.../domain/model/Post.java` |
| In-port pattern | `backend/.../domain/port/in/CreatePostUseCase.java` |
| Out-port pattern | `backend/.../domain/port/out/PostRepository.java` |
| Persistence adapter pattern | `backend/.../adapter/out/persistence/PostPersistenceAdapter.java` |
| JPA entity pattern | `backend/.../adapter/out/persistence/PostJpaEntity.java` |
| REST controller pattern | `backend/.../adapter/in/web/PostController.java` |
| Request DTO pattern | `backend/.../adapter/in/web/dto/CreatePostRequest.java` |
| Global error handler | `backend/.../adapter/in/web/GlobalExceptionHandler.java` |
| Frontend API service | `frontend/src/api/` |
| Frontend page pattern | `frontend/src/pages/posts/PostListPage.tsx` |
| MUI theme | `frontend/src/theme.ts` |
| Full DB schema | `docs/database/schema.sql` |
