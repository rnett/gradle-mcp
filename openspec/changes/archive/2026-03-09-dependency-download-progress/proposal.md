## Why

Currently, when the MCP server resolves dependency sources (during `inspect_dependencies` with `downloadSources=true` or when refreshing source caches for `read_dependency_sources`), the progress reported to the user can be coarse or
missing during the actual download phase. For projects with many dependencies, downloading sources can take a significant amount of time, and the user might perceive a lack of progress. Additionally, source extraction and indexing could
benefit from more granular feedback to distinguish between these two phases for each artifact.

## What Changes

- **Progress Reporting in Gradle Task**: Modify `McpDependencyReportTask` (in `dependencies-report.init.gradle.kts`) to report progress while resolving source artifacts. It will calculate the total number of dependencies to resolve
  beforehand and emit granular progress updates (e.g., "Resolving 10/45 sources") to stdout.
- **Enhanced Sub-status Handling**: Update `DefaultBuildExecutionService` and `RunningBuild` to parse and display more detailed sub-status information, including percentages for individual downloads if available from Gradle's `StatusEvent`
  or the custom report task.
- **Granular Source Processing Progress**: Refine `DefaultSourcesService.processDependencies` to report more granular progress, explicitly distinguishing between "Extracting" and "Indexing" phases for each dependency in the
  `ProgressReporter` message.

## Capabilities

### New Capabilities

- `dependency-resolution-progress`: Detailed tracking and reporting of dependency resolution and artifact downloading (especially sources).
- `source-processing-granular-progress`: Detailed progress for source extraction and indexing operations.

### Modified Capabilities

- `inspect-dependencies`: Improved user feedback when `downloadSources=true`.
- `read-dependency-sources`: Improved user feedback during initial source refresh.

## Impact

- `DefaultSourcesService.kt`: Updated `processDependencies` to report more granular progress.
- `McpDependencyReportTask` (in `dependencies-report.init.gradle.kts`): Added logic to track and report source resolution progress.
- `DefaultBuildExecutionService.kt`: Better parsing and display of status events and custom progress lines.
- `RunningBuild.kt`: Improved `getProgressMessage` and state tracking for better user-facing feedback.
