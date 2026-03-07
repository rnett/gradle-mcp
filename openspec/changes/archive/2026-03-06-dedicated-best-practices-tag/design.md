## Context

Official Gradle documentation includes specific guidelines for best practices and performance. Currently, these are indexed under the generic `userguide` tag because they are located in the `userguide/` subdirectory of the Gradle
distribution. This makes it difficult for agents and users to specifically query for recommended patterns without also retrieving general guide content.

## Goals / Non-Goals

**Goals:**

- Enable specialized searching via a new `best-practices` tag.
- Expose "Best Practices" as a top-level documentation section in the tool's summary output.
- Support documents having multiple tags (e.g., a page can be both `userguide` and `best-practices`).
- Update agent-facing tool metadata and skills to leverage this new tag.

**Non-Goals:**

- Physical relocation of files in the Gradle documentation distribution or cache.
- Manual tagging of files; the process must be automated based on path patterns.

## Decisions

### 1. Multi-Valued Tag Field in Lucene

Lucene documents will be updated to store multiple values for the `tag` field.

- **Rationale**: A page like `userguide/best_practices_general.md` is fundamentally part of the `userguide` but also a `best-practice`. Multi-valued fields allow it to be found via `tag:userguide` (general search) and `tag:best-practices` (
  surgical search).
- **Alternatives**: Using a separate `best_practices` boolean field. However, this would require special-casing the query parser or expecting users to know a new field name. Re-using the `tag` field is more idiomatic for our current tool
  interface.

### 2. Automated Tagging Logic

Refactor `GradleDocsIndexService` to use a `detectTags(relativePath: String): List<String>` method.

- **Criteria**: Any path containing `best_practices` (e.g., `userguide/best_practices_*.md`) will receive the `best-practices` tag in addition to its primary section tag.

### 3. Integrated Section Summarization

Update `GradleDocsService.summarizeSections` to report the new tag.

- **Approach**: The summarization logic will be updated to scan for the same `best-practices` patterns used during indexing to ensure consistency between what is reported as available and what is searchable.

## Risks / Trade-offs

- **[Risk]** Index Inconsistency: Existing indexes in the user's cache will not have the new tags.
- **[Mitigation]** The change will include a mechanism (or a manual instruction for now) to invalidate the documentation index. Since this is an internal tool, a one-time re-index is acceptable.
- **[Trade-off]** Token usage: Adding more tags to search results increases the text returned to the LLM slightly. This is offset by the improved search precision, which prevents the agent from reading irrelevant guide content.
