## ADDED Requirements

### Requirement: Intelligent Auto-Expansion

The `query_build` tool SHALL automatically expand to a detailed view if exactly one component (task, test, failure, problem) matches the provided query.

#### Scenario: Unique task path match

- **WHEN** user calls `query_build(kind="TASKS", query=":app:assemble")`
- **THEN** system returns full task details, including duration and output status, instead of a summary list

#### Scenario: Multiple matches

- **WHEN** user calls `query_build(kind="TASKS", query=":app:")` and multiple tasks exist
- **THEN** system returns a summary list with a hint to refine the query for details

### Requirement: Consolidated Build Component Outcome

The system SHALL provide a unified `BuildComponentOutcome` enum that can be used to filter both tasks and tests in `query_build`.

#### Scenario: Filter failed tests

- **WHEN** user calls `query_build(kind="TESTS", outcome="FAILED")`
- **THEN** system returns only tests with `FAILED` status

#### Scenario: Filter success tasks

- **WHEN** user calls `query_build(kind="TASKS", outcome="PASSED")`
- **THEN** system returns tasks with `SUCCESS` or `UP_TO_DATE` status

### Requirement: Console Result instruction

The output of `wait_build` and `query_build` (when matches are > 1) SHALL explicitly mention `query_build` as the primary way to retrieve full logs or more information.

#### Scenario: Wait build hint

- **WHEN** a `wait_build` call completes
- **THEN** the returned message includes "See query_build(kind='CONSOLE', buildId='...') for full logs."
