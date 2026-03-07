## 1. Core Implementation

- [x] 1.1 Refactor `GradleDocsIndexService.detectTag` to `detectTags` returning `List<String>`.
- [x] 1.2 Implement automated `best-practices` tag detection based on path patterns in `detectTags`.
- [x] 1.3 Update `DefaultGradleDocsIndexService.ensureIndexed` loop to add multiple `tag` fields to Lucene documents.
- [x] 1.4 Update `DefaultGradleDocsService.summarizeSections` to include the `best-practices` tag in its output.

## 2. Tool & Metadata Updates

- [x] 2.1 Update `GradleDocsTools.kt` metadata for `QueryGradleDocsArgs.query` to explicitly document `tag:best-practices`.
- [x] 2.2 Add `best-practices` tag to the "Available Documentation Tags" section of `skills/researching_gradle_internals/SKILL.md`.
- [x] 2.3 Update `skills/gradle_expert/SKILL.md` to recommend using the `best-practices` tag when auditing builds.

## 3. Verification & Cleanup

- [x] 3.1 Create or update an integration test (e.g., `GradleDocsIndexServiceTest`) to verify multi-tagging.
- [x] 3.2 Manually verify that `gradle_docs(query="tag:best-practices")` returns correct results.
- [x] 3.3 Confirm "Best Practices" appears in the summary when calling `gradle_docs()` with no arguments.
- [x] 3.4 Document the need to clear `reading_gradle_docs` cache directories for existing users to see the new tags.
