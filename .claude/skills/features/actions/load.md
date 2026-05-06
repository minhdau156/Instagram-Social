# Load Action

1. Check $ARGUMENTS (after "load"):
   - If it looks like a filename (single word, no spaces): Look for `context/features/{name}.md` OR `context/fixes/{name}.md`
   - If it's multiple words: Use as inline feature description, generate goals
   - If empty: Error - "load" requires a spec filename or feature description

2. Update current-feature.md:
   - Update H1 heading to include feature name (e.g., `# Current Feature: Do task 0-1`)
   - Check out to feature branch instead of main
   - Write goals as bullet points under ## Goals
   - Write any additional notes/context under ## Notes
   - Set Status to "Not Started"
   - Write unit test for this feature

3. Confirm spec loaded and show the feature summary