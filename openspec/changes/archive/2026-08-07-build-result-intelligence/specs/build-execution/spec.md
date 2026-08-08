# Capability: build-execution

## Purpose

Preserve the task outcome reason already derived at task completion so downstream build-result queries can explain reused and skipped work.

## ADDED Requirements

### Requirement: Task completion stores the derived outcome reason

When task execution finishes, the build execution service SHALL populate nullable `TaskResult.reason` from the already-derived outcome and skip state. `TaskResult.outcome` SHALL be one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. `SUCCESS`, `FAILED`, and `CANCELLED` SHALL use a null reason. `FROM_CACHE` SHALL use `FROM_CACHE: isFromCache=true`, and `UP_TO_DATE` SHALL use `UP_TO_DATE: isUpToDate=true`. A `TaskSkippedResult` whose `skipMessage` triggered the `NO_SOURCE` mapping in `BuildExecutionService:283` SHALL retain outcome `NO_SOURCE` and use `NO-SOURCE: <skipMessage verbatim>`; it SHALL never be collapsed to `SKIPPED`. Every other `TaskSkippedResult` SHALL use outcome `SKIPPED` and `SKIPPED: <skipMessage verbatim>`.

#### Scenario: Finished task came from cache

- **WHEN** task-finish handling determines that a task result came from cache
- **THEN** the stored task result has outcome `FROM_CACHE`
- **AND** `reason` is `FROM_CACHE: isFromCache=true`

#### Scenario: Finished task was up to date

- **WHEN** task-finish handling determines that a task was up to date
- **THEN** the stored task result has outcome `UP_TO_DATE`
- **AND** `reason` is `UP_TO_DATE: isUpToDate=true`

#### Scenario: Finished task had no source

- **WHEN** task-finish handling receives the `TaskSkippedResult.skipMessage` that triggers the `NO_SOURCE` mapping in `BuildExecutionService:283`
- **THEN** the stored task result has outcome `NO_SOURCE`
- **AND** `reason` is `NO-SOURCE: <skipMessage verbatim>`
- **AND** the outcome is not collapsed to `SKIPPED`

#### Scenario: Finished task was skipped for another reason

- **WHEN** task-finish handling receives a non-empty skip message that does not indicate no source
- **THEN** the stored task result has outcome `SKIPPED`
- **AND** `reason` is `SKIPPED: <skipMessage verbatim>`

#### Scenario: Finished task executed or was cancelled

- **WHEN** task-finish handling records a successful, failed, or cancelled task
- **THEN** the stored task result has the corresponding `SUCCESS`, `FAILED`, or `CANCELLED` outcome
- **AND** `reason` is null
