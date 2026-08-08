# Capability: build-execution

## MODIFIED Requirements

### Requirement: Task completion stores the derived outcome reason

When task execution finishes, the build execution service SHALL populate nullable `TaskResult.reason` from the already-derived outcome and skip state. `TaskResult.outcome` SHALL be one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, and `UP_TO_DATE` SHALL use a null reason. A `TaskSkippedResult` whose `skipMessage` triggered the `NO_SOURCE` mapping in `BuildExecutionService:283` SHALL retain outcome `NO_SOURCE` and use `reason` equal to `skipMessage` verbatim (`NO-SOURCE`); it SHALL never be collapsed to `SKIPPED`. Every other `TaskSkippedResult` SHALL use outcome `SKIPPED` and `reason` equal to `skipMessage` verbatim (no `SKIPPED: ` prefix).

#### Scenario: Finished task came from cache

- **WHEN** task-finish handling determines that a task result came from cache
- **THEN** the stored task result has outcome `FROM_CACHE`
- **AND** `reason` is null

#### Scenario: Finished task was up to date

- **WHEN** task-finish handling determines that a task was up to date
- **THEN** the stored task result has outcome `UP_TO_DATE`
- **AND** `reason` is null

#### Scenario: Finished task had no source

- **WHEN** task-finish handling receives the `TaskSkippedResult.skipMessage` that triggers the `NO_SOURCE` mapping in `BuildExecutionService:283`
- **THEN** the stored task result has outcome `NO_SOURCE`
- **AND** `reason` is the verbatim skip message (`NO-SOURCE`)
- **AND** the outcome is not collapsed to `SKIPPED`

#### Scenario: Finished task was skipped for another reason

- **WHEN** task-finish handling receives a non-empty skip message that does not indicate no source
- **THEN** the stored task result has outcome `SKIPPED`
- **AND** `reason` is the verbatim skip message (no `SKIPPED: ` prefix)

#### Scenario: Finished task executed, reused, or was cancelled

- **WHEN** task-finish handling records a successful, failed, cancelled, from-cache, or up-to-date task
- **THEN** the stored task result has the corresponding `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, or `UP_TO_DATE` outcome
- **AND** `reason` is null
