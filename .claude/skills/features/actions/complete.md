# Complete Action

## Step 1 — Verify and Tick the Task File Checklist

Locate the task file under `docs/tasks/` that corresponds to the current task (e.g., `docs/tasks/phase-9/TASK-9.11-tests.md`).

Read the file and go through **every checklist item** (`- [ ]`):

- For each item, verify the implementation exists (grep for the class/method/test/endpoint described).
- If the criterion is done, change `- [ ]` to `- [x]` in the file.
- If a criterion is **not done**, stop here. Do not proceed to git. Complete the missing work first, then return to this step.

Only continue to Step 2 when **every `- [ ]` in the task file is `- [x]`**.

> If there is no task file for the current task, skip this step and proceed to Step 2.

---

## Step 2 — Verify the `current-feature.md` Acceptance Criteria

Read `context/current-feature.md` and locate the **Acceptance Criteria** section.

For each checklist item:
- If done, mark it `- [x]`.
- If **not done**, stop. Complete the missing work first.

Only continue to Step 3 when **every item is `- [x]`**.

> If there is no Acceptance Criteria section, ask the user to confirm all goals are met before continuing.

---

## Step 3 — Commit the Feature Work

1. `git status` — confirm you are on the feature branch and all intended files are present.
2. Stage all relevant files: `git add <files>` (prefer explicit paths over `-A`).
3. Commit with a descriptive Conventional Commit message:
   ```
   feat(moderation): add report and block domain model
   ```
   **Do not add "Claude" or "Co-Authored-By" to the message.**

---

## Step 4 — Merge into Main

1. `git checkout main`
2. `git merge --no-ff <feature-branch>` — keep the merge commit for traceability.
3. Delete the local feature branch: `git branch -d <feature-branch>`

---

## Step 5 — Reset `current-feature.md`

Edit `context/current-feature.md`:
- Change the H1 back to `# Current Feature`
- Set **Status** to `Not Started`
- Clear **Goals** body (keep the `<!-- -->` placeholder comment)
- Clear **Notes** body (keep the `<!-- -->` placeholder comment)
- Append a one-line summary to the **end** of the History list:
  ```
  - TASK-X.Y — <short summary of what was built>
  ```

---

## Step 6 — Commit the Reset

```
git add context/current-feature.md
git commit -m "chore: reset current-feature.md after completing [feature]"
```

---

## Step 7 — Push to Origin (single push)

```
git push origin main
```

Push **once**, after both the feature merge commit and the reset commit are on main.

---

## Step 8 — Clean Up Remote Branch (if applicable)

If the feature branch was previously pushed to origin:
```
git push origin --delete <feature-branch>
```
