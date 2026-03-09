## 1. Gradle Task Progress Reporting

- [x] 1.1 Update `McpDependencyReportTask` in `dependencies-report.init.gradle.kts` to count components to resolve
- [x] 1.2 Implement progress reporting in `gatherSources` using `println` with `[gradle-mcp] [PROGRESS]` prefix
- [x] 1.3 Implement similar progress reporting in `gatherLatestVersions` if applicable

## 2. Server-side Progress Parsing

- [x] 2.1 Update `DefaultBuildExecutionService.processTaskOutput` to recognize and parse `[PROGRESS]` lines
- [x] 2.2 Update `RunningBuild` to store and expose sub-task progress (completed/total)
- [x] 2.3 Refine `DefaultBuildExecutionService` status event handling to extract percentages from Gradle events

## 3. User-facing Progress Improvements

- [x] 3.1 Update `RunningBuild.getProgressMessage()` to include percentage-based sub-status
- [x] 3.2 Update `DefaultSourcesService.processDependencies` to report separate extraction and indexing steps
- [x] 3.3 Verify overall progress smoothness during a sample `inspect_dependencies` call with `downloadSources=true`
