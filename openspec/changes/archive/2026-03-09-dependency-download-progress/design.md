## Context

Currently, Gradle's `StatusEvent`s are partially integrated into `RunningBuild` progress, but could be more descriptive. Source resolution happens within a Gradle task (`mcpDependencyReport`) and doesn't currently report its internal
progress back to the MCP server's `ProgressReporter`. Source processing (extraction and indexing) is handled by `DefaultSourcesService` and currently uses a single message per dependency.

## Goals / Non-Goals

**Goals:**

- Provide real-time feedback during slow source resolution/downloading in Gradle.
- Enhance the accuracy and descriptive quality of the progress bar in the client.
- Distinguish between extraction and indexing phases in source processing.

**Non-Goals:**

- Implementing a full-blown progress tracking system for all Gradle tasks (focused on dependencies).
- Changing the fundamental way `ProgressReporter` works.

## Decisions

1. **Stdout-based Progress from Gradle Task**:
    - **Decision**: `McpDependencyReportTask` will print `[gradle-mcp] [PROGRESS] [SOURCE_RESOLUTION] <completed>/<total> <message>` lines to stdout.
    - **Rationale**: The `DefaultBuildExecutionService` already intercepts stdout. This is the simplest way to get data out of the running Gradle process without complex listeners or socket communication.
    - **Alternatives**: Using `InternalProgressListener` (too unstable/complex), writing to a temporary file (requires polling).

2. **Parsing StatusEvents for Percentages**:
    - **Decision**: `DefaultBuildExecutionService` will update `RunningBuild` with percentage information from `StatusEvent` when `event.total > 0`.
    - **Rationale**: `RunningBuild` currently only handles `completedItems / totalItems` where items are tasks. Including the "sub-task" percentage provides much smoother progress.

3. **Refined Source Processing Messages**:
    - **Decision**: Update `DefaultSourcesService.processDependencies` to report separate "Extracting" and "Indexing" steps for each artifact by updating the leftmost part of the progress message.
    - **Rationale**: Indexing can take as long as extraction for some artifacts. Updating the phase label while keeping the dependency ID constant provides clear, granular feedback without confusing the user about which artifact is being
      processed.

## Risks / Trade-offs

- **[Risk]** Excessive stdout output from the task could bloat logs. -> **Mitigation**: Only emit progress updates when the count of resolved artifacts changes.
- **[Risk]** Complexity in `RunningBuild` progress calculation. -> **Mitigation**: Keep the calculation simple: `(completed + subTaskPercentage) / total`.
