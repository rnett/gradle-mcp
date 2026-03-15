## ADDED Requirements

### Requirement: Inspect Build Tool Progress Reporting

The `inspect_build` tool SHALL report progress to the client when waiting for a background build to complete or reach a specific state.

#### Requirement: `afterCall` parameter

- **WHEN** `inspect_build` is called with a `wait` parameter and `afterCall` is `true`
- **THEN** it SHALL only consider events that occur after the current tool call started. This prevents immediate returns for events that have already occurred.

#### Requirement: Progress Heuristics

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
