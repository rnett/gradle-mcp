## Why

Official Gradle best practices and performance guidelines are currently mixed in with general user guide content under the `userguide` tag, making them difficult to target specifically during research. A dedicated `best-practices` tag
enables surgical discovery of recommended patterns and optimizations, which is essential for high-quality build logic maintenance and agent-led architectural audits.

## What Changes

- **Multi-Tag Support**: Refactor `GradleDocsIndexService` to allow documents to have multiple tags.
- **Automated Tagging**: Enhance the indexing process to automatically detect and apply the `best-practices` tag to relevant files (e.g., those in `userguide/best_practices_*.md`).
- **Section Summarization**: Update `GradleDocsService` to include "Best Practices" in the available documentation sections list.
- **Tool Guidance**: Update the `gradle_docs` tool metadata to explicitly inform users and agents about the new `tag:best-practices` option.
- **Skill Enrichment**: Update project skills to incorporate the `best-practices` tag into standard research workflows.

## Capabilities

### New Capabilities

- `best-practices-tagging`: Implementation of automated categorization for best practice documentation during the indexing phase.

### Modified Capabilities

- `gradle-docs-querying`: Enhances the existing documentation search and summary system to recognize and prioritize the `best-practices` tag.

## Impact

- **`GradleDocsIndexService`**: Internal indexing logic changes to support multi-valued tag fields.
- **`GradleDocsService`**: Updates to section summarization to expose the new tag.
- **`GradleDocsTools`**: Metadata updates to improve discoverability for both humans and AI agents.
- **Research Skills**: Improved precision in finding authoritative Gradle recommendations.
