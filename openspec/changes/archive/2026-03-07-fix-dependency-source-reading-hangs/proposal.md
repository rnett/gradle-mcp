## Why

Dependency source reading tools (`read_dependency_sources`, `search_dependency_sources`) are inconsistently getting stuck and hanging. This behavior blocks agentic workflows, increases token usage due to retries, and disrupts the
research-strategy-execution cycle. A thorough audit is needed to identify root causes, likely related to file locking or resource contention during the extraction and indexing of dependency sources.

## What Changes

- **Investigate and Audit**: Comprehensive analysis of the `DependencySourceService` and its interaction with the Gradle Tooling API and file system.
- **File Locking Refactor**: Audit and fix potentially deadlocking or long-waiting file lock implementations in the dependency source extraction logic.
- **Robust Timeouts**: Implement granular timeouts and cancelability for long-running source extraction and indexing tasks.
- **Diagnostic Logging**: Enhance logging within the dependency source tools to provide clear feedback when operations are slow or blocked.
- **Resource Cleanup**: Ensure all file handles and temporary directories are reliably cleaned up, even on failure or interruption.

## Capabilities

### New Capabilities

- `dependency-source-reliability`: Ensuring that dependency source retrieval and indexing are deterministic, performant, and fail gracefully with clear feedback instead of hanging.

### Modified Capabilities

<!-- No requirement changes to existing high-level capabilities, just internal reliability improvements. -->

## Impact

- `DependencySourceService`: The primary service responsible for source extraction and indexing.
- `GradleProvider`: The orchestrator for Gradle-related operations.
- `search_dependency_sources` / `read_dependency_sources` tools: User-facing tools that will benefit from improved reliability.
- Disk I/O and CPU: Indexing and extraction logic will be audited for performance and resource usage.
