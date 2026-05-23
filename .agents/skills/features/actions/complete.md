# Complete Action

## Step 0 — Tick the task checklist

1. Read `context/current-feature.md` to get the task name from the `# Current Feature` heading (e.g. `TASK-9.1 — Domain Models: Report, UserBlock`).
2. Derive the task file path: look under `docs/tasks/phase-<N>/` for the file whose name starts with the task ID (e.g. `docs/tasks/phase-9/TASK-9.1-domain-models.md`). Use Glob `docs/tasks/**/<TASK-ID>*` if unsure.
3. Read the task file and locate the `## Checklist` section.
4. For each `- [ ]` item, verify it is satisfied by the actual code (read the relevant files if needed).
   - If satisfied → change `- [ ]` to `- [x]` in the task file.
   - If NOT satisfied → stop, implement the missing item, then return here.
5. All items must be `- [x]` before proceeding to the next steps.

---

## Steps 1–7 — Commit, merge, push

1. Stage all feature files and commit with a descriptive conventional-commit message.
2. Switch to main and merge the feature branch (no push yet). Skip if already on main.
3. Delete the local feature branch. Skip if work was done directly on main.
4. Reset `context/current-feature.md`:
   - Change H1 back to `# Current Feature`
   - Clear Goals and Notes sections (leave the headings, remove content)
   - Prepend a one-line summary of the completed task to the top of the `## History` list
5. Commit the reset: `chore: reset current-feature.md after completing [TASK-ID]`
6. Push main to origin ONCE (single push with all commits).
7. If the feature branch was previously pushed to origin, delete it from origin.
