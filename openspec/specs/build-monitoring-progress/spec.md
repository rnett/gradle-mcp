# Capability: build-monitoring-progress

## Purpose

Defines how the MCP server reports progress for Gradle builds, including granular stdout-based sub-task progress, frozen completed-build snapshots, error context in summaries, and progress heuristics for long-running operations.
## Requirements
### Requirement: Query Build Tool Progress Reporting

The `wait_build` tool SHALL report progress to the client when waiting for a background build to complete or reach a specific state.

#### Scenario: `afterCall` parameter

- **WHEN** `wait_build` is called with a `timeout` parameter and `afterCall` is `true`
- **THEN** it SHALL only consider events that occur after the current tool call started. This prevents immediate returns for events that have already occurred.

#### Scenario: Progress Heuristics

- **PROGRESS CAP**: All progress reported for a running build SHALL be capped at 99% (0.99) to avoid jumping to 100% before the build has officially finished and reported its outcome.
- **UNKNOWN TOTAL CURVE**: When the total number of items in a phase is unknown, progress SHALL be calculated using an asymptotic curve: `completed / (completed + 1)`.

### Requirement: Stdout Progress Protocol

The build system SHALL support a structured stdout-based progress protocol for granular sub-task reporting.

#### Scenario: Reporting sub-task progress

- **WHEN** a task or script emits a line in the format `[gradle-mcp] [PROGRESS] [CATEGORY]: [CURRENT]/[TOTAL]: [MESSAGE]`
- **THEN** the `BuildProgressTracker` SHALL capture this line and update the granular progress for that category.

#### Scenario: Reporting sub-task total

- **WHEN** a task or script emits a line in the format `[gradle-mcp] [PROGRESS] [CATEGORY]: TOTAL: [TOTAL]`
- **THEN** the `BuildProgressTracker` SHALL set the total items for that category.

### Requirement: Build Summary Error Context

When a build ID is provided to query_build in summary mode, the response SHALL include the first few lines of actual failure messages if the build has errors.

#### Scenario: Display recent error context in summary

- **WHEN** query_build(buildId="...") is called for a build with failures
- **THEN** the output SHALL include a "Recent Error Context" section.
- **AND** it SHALL show the message and top lines of the description for up to 3 failures.

### Requirement: Granular Progress Reporting Standards

Progress reporting SHALL prioritize accuracy and user feedback for long-running operations.

- **Reporting Frequency**: Producers SHALL NOT artificially limit or throttle reports (e.g., `if count % 100`). Throttling is handled authoritatively at the top level.
- **Parallel Strategy**: Progress for parallel operations SHALL prioritize stable activity messaging and independent phase ranges over jittery, fluctuating percentages.
- **Merging Progress**: Merging progress across multiple search providers MUST be based on the total document count across all providers.
- **Filtering Differentiation**: Progress messaging in init scripts SHALL distinguish between items "skipped by filter" and those that are "up-to-date" to provide accurate feedback on work performed.
- **Job Management**: Background collection jobs SHALL use `job.cancelAndJoin()` to ensure clean termination when cancelled.
- **Eventual Consistency**: Progress trackers SHOULD be designed for eventual consistency to avoid heavy synchronization overhead from multiple Gradle listener threads.

### Requirement: Active Operations Visibility

The build summary output SHALL explicitly list currently running tasks if the build is still in progress.

#### Scenario: Display active tasks in summary

- **WHEN** query_build(buildId="...") is called for a running build

### Requirement: Full Export to File

The `query_build` tool SHALL support an `outputFile` parameter to write the entire tool response to a file on the host file system, bypassing all pagination limits.

#### Scenario: Exporting large console logs

- **WHEN** `query_build` is called with a `buildId`, `kind="CONSOLE"`, and an `outputFile` path
- **THEN** the system SHALL write the full console output to the specified file.
- **AND** the tool response SHALL indicate the file path and total size of the output.
- **AND** the system SHALL NOT apply pagination or truncation to the file content.

### Requirement: Completed builds retain frozen phase counts

A completed build result SHALL include frozen `phaseCounts` with exactly three buckets: `configuration`, `dependency-resolution`, and `task-execution`. Source `PhaseState` values SHALL be retained in `completedPhaseHistory` when each phase finishes and before that state is removed from the private active stack; at completion, `RunningBuild` SHALL freeze that retained history into the `Build` rather than read live progress state. Classification SHALL trim each retained phase name, match case-insensitively, and apply one top-down first-match precedence: `configuration` for `^(CONFIGURATION|configure\b.*|configuration\b.*|project configuration\b.*)$`, then `dependency-resolution` for `^(.*dependency.*resolution.*|.*resolve.*dependenc.*|resolve dependencies\b.*)$`, then `task-execution` for `^(.*task.*execution.*|.*execute.*tasks?.*|.*run.*tasks?.*|task execution\b.*)$`. Overlap SHALL resolve to the first matching bucket. Each classified retained state SHALL add its `totalItems` and `completedItems` to that bucket; unmatched names SHALL be ignored. Every bucket SHALL be emitted, and an absent bucket SHALL be `{totalItems:0, completedItems:0}`, including `dependency-resolution` when no distinct phase was observed.

#### Scenario: Build completion freezes phase state

- **WHEN** build execution reaches a completed state
- **THEN** `RunningBuild` freezes configuration, dependency-resolution, and task-execution counts from retained completed-phase history into the `Build`
- **AND** each bucket contains aggregated total and completed items
- **AND** later progress-tracker mutation cannot change the completed result

#### Scenario: Completed phases remain available after active state is dropped

- **WHEN** a phase finishes and its `PhaseState` is removed from the active stack
- **THEN** its name, total items, and completed items remain in retained completed-phase history
- **AND** build completion freezes that retained history into `phaseCounts`

#### Scenario: Multiple retained phases aggregate into one bucket

- **WHEN** multiple retained phase states classify to the same bucket
- **THEN** that bucket's `totalItems` is the sum of their total items
- **AND** that bucket's `completedItems` is the sum of their completed items

#### Scenario: Unmatched and absent phases retain the fixed output shape

- **WHEN** a retained phase name matches no bucket and no distinct dependency-resolution phase was observed
- **THEN** the unmatched phase is ignored
- **AND** `phaseCounts` still emits exactly configuration, dependency-resolution, and task-execution
- **AND** dependency-resolution is `{totalItems:0, completedItems:0}`

#### Scenario: Agent reads completed phase costs

- **WHEN** an agent inspects a completed build result
- **THEN** it can compare total and completed counts across configuration, dependency resolution, and task execution
- **AND** the counts are a frozen snapshot of completed phases at build completion
- **AND** the counts do not reflect later live progress state

