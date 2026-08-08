# Capability: task-provenance-reporting

## MODIFIED Requirements

### Requirement: Task provenance includes the outcome reason

Each surfaced task result SHALL include its outcome, nullable `reason`, and provenance. `TaskResult.outcome` SHALL be one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. The reason policy SHALL preserve the authoritative outcome and hold the verbatim Gradle skip message only for skipped outcomes: `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, and `UP_TO_DATE` SHALL all use null; a `TaskSkippedResult` whose `skipMessage` triggered the `NO_SOURCE` mapping in `BuildExecutionService:283` SHALL retain outcome `NO_SOURCE` and use `reason` equal to `skipMessage` verbatim (`NO-SOURCE`); and every other `TaskSkippedResult` SHALL use outcome `SKIPPED` and `reason` equal to `skipMessage` verbatim (no `SKIPPED: ` prefix). `NO_SOURCE` SHALL never be collapsed to `SKIPPED`.

#### Scenario: No explanatory reason for a cache result

- **WHEN** a task result has outcome `FROM_CACHE`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is null

#### Scenario: No explanatory reason for an up-to-date result

- **WHEN** a task result has outcome `UP_TO_DATE`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is null

#### Scenario: Provenance accompanies a no-source reason

- **WHEN** a `TaskSkippedResult` skip message triggers the `NO_SOURCE` mapping in `BuildExecutionService:283`
- **THEN** the surfaced result includes its provenance with outcome `NO_SOURCE`
- **AND** `reason` is the verbatim skip message (`NO-SOURCE`)
- **AND** the outcome is not collapsed to `SKIPPED`

#### Scenario: Provenance accompanies another skipped reason

- **WHEN** a skipped task has a non-empty skip message that does not indicate no source
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is the verbatim skip message with no `SKIPPED: ` prefix

#### Scenario: Executed and reused outcomes have no explanatory reason

- **WHEN** a task result has outcome `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, or `UP_TO_DATE`
- **THEN** the surfaced result includes its provenance
- **AND** `reason` is absent or null
