# Capability: task-provenance-reporting

## Purpose

Extend task provenance results with the reason for non-executed task outcomes so agents can explain both where a task came from and why its work was reused or skipped.

## ADDED Requirements

### Requirement: Task provenance includes the outcome reason

Each surfaced task result SHALL include its outcome, nullable `reason`, and provenance. `TaskResult.outcome` SHALL be one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. The reason policy SHALL preserve the authoritative outcome: `SUCCESS`, `FAILED`, and `CANCELLED` SHALL use null; `FROM_CACHE` SHALL use `FROM_CACHE: isFromCache=true`; `UP_TO_DATE` SHALL use `UP_TO_DATE: isUpToDate=true`; a `TaskSkippedResult` whose `skipMessage` triggered the `NO_SOURCE` mapping in `BuildExecutionService:283` SHALL retain outcome `NO_SOURCE` and use `NO-SOURCE: <skipMessage verbatim>`; and every other `TaskSkippedResult` SHALL use outcome `SKIPPED` and `SKIPPED: <skipMessage verbatim>`. `NO_SOURCE` SHALL never be collapsed to `SKIPPED`.

#### Scenario: Provenance accompanies a cache reason

- **WHEN** a task result has outcome `FROM_CACHE`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is `FROM_CACHE: isFromCache=true`

#### Scenario: Provenance accompanies an up-to-date reason

- **WHEN** a task result has outcome `UP_TO_DATE`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is `UP_TO_DATE: isUpToDate=true`

#### Scenario: Provenance accompanies a no-source reason

- **WHEN** a `TaskSkippedResult` skip message triggers the `NO_SOURCE` mapping in `BuildExecutionService:283`
- **THEN** the surfaced result includes its provenance with outcome `NO_SOURCE`
- **AND** `reason` is `NO-SOURCE: <skipMessage verbatim>`
- **AND** the outcome is not collapsed to `SKIPPED`

#### Scenario: Provenance accompanies another skipped reason

- **WHEN** a skipped task has a non-empty skip message that does not indicate no source
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is `SKIPPED: <skipMessage verbatim>`

#### Scenario: Executed and cancelled outcomes have no explanatory reason

- **WHEN** a task result has outcome `SUCCESS`, `FAILED`, or `CANCELLED`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is absent or null
