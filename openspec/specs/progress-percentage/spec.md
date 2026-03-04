# progress-percentage Specification

## Purpose

TBD - created by archiving change gradle-progress-percent. Update Purpose after archive.

## Requirements

### Requirement: Build phase progress calculation

The system SHALL calculate the 0-100% completion for each active build phase based on `BuildPhaseStartEvent` and item-level finish events (e.g., `TaskFinishEvent`).

#### Scenario: Configuration phase progress reporting

- **WHEN** a `BuildPhaseStartEvent` for `CONFIGURE_BUILD` is received with 10 projects
- **THEN** progress SHALL move from 0% to 100% as projects are configured
- **AND** messages SHALL be prefixed with `[CONFIGURING]`

#### Scenario: Execution phase progress reporting

- **WHEN** a `BuildPhaseStartEvent` for `RUN_MAIN_TASKS` is received with 100 tasks
- **THEN** progress SHALL move from 0% to 100% as tasks are finished
- **AND** messages SHALL be prefixed with `[EXECUTING]`

### Requirement: Smooth progress interpolation

The system SHALL use `StatusEvent` data to smoothly interpolate progress within the current phase.

#### Scenario: Smooth download progress within phase

- **WHEN** 5/10 tasks are finished in the execution phase (50% phase progress)
- **AND** task 6 is downloading a file reported via `StatusEvent` at 50% completion
- **THEN** the progress SHALL reflect 55% within the phase range (5 + 0.5)/10

### Requirement: Handling unknown total

The system SHALL gracefully handle cases where the total work is unknown (e.g., `getTotal()` returns -1).

#### Scenario: Progress notification without percentage

- **WHEN** a `StatusEvent` is received where `getTotal()` is -1
- **THEN** the system SHALL emit a progress notification to the MCP client containing only the status message and no percentage

