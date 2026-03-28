## Why

The current Gradle MCP tools can sometimes provide cryptic or incomplete feedback when tasks are running, failing, or missing. This makes it difficult for users (and AI agents) to understand the state of their build, troubleshoot test
failures, or navigate large outputs efficiently. Standardizing pagination metadata, grouping test results, and providing more context for errors will significantly improve the developer experience and tool reliability.

## What Changes

- **Improved Build Summaries**: `inspect_build` in summary mode now includes recent error context (actual failure messages) and active operations when a build ID is provided.
- **Enhanced Task Feedback**: Better error messages for missing or running tasks, including lists of executed tasks and specific guidance for long-running tasks like `run`.
- **Suite-Based Test Grouping**: Test failures and results are now grouped by suite in build summaries for better readability.
- **Advanced Pagination**: Added "tail" mode support to pagination metadata, providing clearer range indicators (e.g., "last 100 lines") and follow-up instructions for log tailing.
- **Precise Symbol Search**: Formalized the `DECLARATION` search mode to support `name` and `fqn` field filtering, non-tokenized FQN matches, and regex-based FQN searching.
- **Task Output Safety**: Added timeout warnings and improved feedback for `captureTaskOutput` to handle long-running builds and missing task outputs more gracefully.

## Capabilities

### New Capabilities

- None (refining existing behaviors).

### Modified Capabilities

- `tool-pagination`: Add "tail" mode support and improved metadata for log tailing.
- `build-monitoring-progress`: Enhance `inspect_build` summary mode with error context and active tasks.
- `test-progress-reporting`: Add requirements for suite-based grouping in test summaries.
- `fqn-symbol-search`: Detail `DECLARATION` search field filtering (`name`, `fqn`) and regex support.
- `task-output-capturing`: Improve feedback for missing or currently running task outputs during capture.

## Impact

Affects `GradleBuildLookupTools`, `GradleExecutionTools`, `DependencySourceTools`, `Pagination`, and `OutputFormatter`. Requires updates to several test suites to reflect the refined data models and output formats. No breaking changes to
the MCP tool signatures themselves, only to the structured text they return.
