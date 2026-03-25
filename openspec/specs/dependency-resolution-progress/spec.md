# Capability: dependency-resolution-progress

## Purpose

Specifies progress reporting during the source resolution phase of the dependency report task, including sub-status and percentage display.

## Requirements

### Requirement: Source Resolution Progress Reporting

The system SHALL report progress during the source resolution phase of the dependency report task. This includes calculating the total number of dependencies to resolve and reporting the current count of completed resolutions.

#### Scenario: Total reported before source resolution

- **WHEN** the `mcpDependencyReport` task is run with `mcp.downloadSources=true`
- **THEN** the task SHALL calculate the total number of unique module components and emit a total message (e.g., `[gradle-mcp] [PROGRESS] [SOURCE_RESOLUTION] TOTAL: 45`)

#### Scenario: Incremental progress reported during source resolution

- **WHEN** the `mcpDependencyReport` task is run with `mcp.downloadSources=true`
- **THEN** the task SHALL emit progress messages to stdout for each resolved artifact (e.g., `[gradle-mcp] [PROGRESS] [SOURCE_RESOLUTION] 1/45: org.gradle:gradle-api`)

### Requirement: Sub-status and Percentage Display

The `RunningBuild.getProgressMessage()` SHALL include the current sub-status and, if available, the percentage of completion for the current operation.

#### Scenario: Sub-status with percentage displayed

- **WHEN** a `StatusEvent` with progress and total is received
- **THEN** the progress message SHALL include the sub-status and the percentage (e.g., `[EXECUTING] :app:mcpDependencyReport (Download http://... - 45%)`)
