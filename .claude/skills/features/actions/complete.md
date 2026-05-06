# Complete Action

- First must check the checklist in the current task having in current feature file
- Must tick the checklist which is completed
- If all task are completed then go to the next step
- If not completed then go back to the task and complete it

1. Stage all changes and commit with a descriptive message
2. Switch to main and merge the feature branch (no push yet)
3. Delete the local feature branch
4. Reset current-feature.md:
   - Change H1 back to `# Current Feature`
   - Clear Goals and Notes sections (keep placeholder comments)
   - Add feature summary to the END of History
5. Commit the reset: `chore: reset current-feature.md after completing [feature]`
6. Push main to origin ONCE (single push with all changes)
7. If feature branch was previously pushed, delete it from origin