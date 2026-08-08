# Capability: build-querying

## MODIFIED Requirements

### Requirement: Task queries include an outcome reason

`query_build` output for `kind=TASKS` SHALL include each `TaskResult`'s outcome, nullable `reason`, and provenance. `TaskResult.outcome` SHALL be one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, and `UP_TO_DATE` SHALL use a null reason. A `TaskSkippedResult` whose `skipMessage` triggered the `NO_SOURCE` mapping in `BuildExecutionService:283` SHALL retain outcome `NO_SOURCE` and use `reason` equal to `skipMessage` verbatim (`NO-SOURCE`); it SHALL never be collapsed to `SKIPPED`. Every other `TaskSkippedResult` SHALL use outcome `SKIPPED` and `reason` equal to `skipMessage` verbatim (no `SKIPPED: ` prefix). TASKS output SHALL print `Reason:` whenever `reason` is non-null, for `NO_SOURCE` and general `SKIPPED` alike.

#### Scenario: Task result came from cache

- **WHEN** a task result has outcome `FROM_CACHE`
- **THEN** TASKS output includes the task outcome and provenance
- **AND** `reason` is null

#### Scenario: Task result was up to date

- **WHEN** a task result has outcome `UP_TO_DATE`
- **THEN** TASKS output includes the task outcome and provenance
- **AND** `reason` is null

#### Scenario: Skipped task had no source

- **WHEN** a `TaskSkippedResult` skip message triggers the `NO_SOURCE` mapping in `BuildExecutionService:283`
- **THEN** TASKS output includes outcome `NO_SOURCE` and provenance
- **AND** `reason` is the verbatim skip message (`NO-SOURCE`)
- **AND** TASKS output prints `Reason:`
- **AND** the outcome is not collapsed to `SKIPPED`

#### Scenario: Skipped task has another skip message

- **WHEN** a skipped task has a non-empty skip message that does not indicate no source
- **THEN** TASKS output includes outcome `SKIPPED` and provenance
- **AND** `reason` is the verbatim skip message (no `SKIPPED: ` prefix)
- **AND** TASKS output prints `Reason:`

#### Scenario: Executed, reused, or cancelled task has no reason

- **WHEN** a task result has outcome `SUCCESS`, `FAILED`, `CANCELLED`, `FROM_CACHE`, or `UP_TO_DATE`
- **THEN** TASKS output includes the task outcome and provenance
- **AND** `reason` is absent or null

### Requirement: Task origin aggregation is exposed in task queries

Completed build data SHALL include a `taskOriginAggregation` map from origin plugin to task count. Tasks whose provenance is absent SHALL be grouped under the single reserved key `_unknown`; `_unknown` SHALL be omitted when every task has provenance, and the sum of all map values SHALL equal the total completed task count. These values MUST be detached from live mutable progress state. `query_build` with `kind="TASKS"` SHALL render the aggregation as a `Task Origins:` section when the map is non-empty. DASHBOARD output, CONSOLE output, and base `Build.toOutputString` output SHALL NOT include `Task Origins:`.

#### Scenario: TASKS output includes task origins

- **WHEN** an agent queries `query_build(kind="TASKS")` for a completed build with task results
- **THEN** TASKS output includes a `Task Origins:` section grouping task counts by origin plugin, using `_unknown` for tasks without provenance
- **AND** the aggregation values sum to the total completed task count

#### Scenario: Dashboard and console omit task origins

- **WHEN** an agent queries DASHBOARD or CONSOLE output for a completed build
- **THEN** the output does not include `Task Origins:`
- **AND** base build result rendering does not include `Task Origins:`
