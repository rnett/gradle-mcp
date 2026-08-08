## Purpose

Defines requirements for querying completed Gradle builds, including task/test filtering, auto-expansion, and console retrieval hints.
## Requirements
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
- **THEN** the returned message includes "See query_build(kind=''CONSOLE'', buildId=''...'') for full logs."

### Requirement: Failed builds route through structured problems first

Agents diagnosing a FAILED build or a low-signal build error MUST query `query_build` with `kind=PROBLEMS` before starting file-read investigation. PROBLEMS output SHALL include each problem''s severity, id, display name, and documentation link, together with per-occurrence details and potential solutions.

#### Scenario: Failed build has aggregated problems

- **WHEN** an agent receives a FAILED build result
- **AND** the build has entries in the generic problem aggregation stream
- **THEN** the agent queries `query_build` with `kind=PROBLEMS` before reading build files
- **AND** the output includes severity, id, display name, documentation link, occurrence details, and potential solutions

#### Scenario: Initial failure message has low diagnostic signal

- **WHEN** an agent receives a low-signal build error
- **AND** the initial message does not identify an actionable cause
- **THEN** the agent queries `query_build` with `kind=PROBLEMS` before entering a file-read loop

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

### Requirement: Completed build queries expose phase counts

Completed build output SHALL include `phaseCounts` with exactly three buckets: `configuration`, `dependency-resolution`, and `task-execution`. Classification SHALL trim each retained phase name, match case-insensitively, and apply one top-down first-match precedence: `configuration` for `^(CONFIGURATION|configure\b.*|configuration\b.*|project configuration\b.*)$`, then `dependency-resolution` for `^(.*dependency.*resolution.*|.*resolve.*dependenc.*|resolve dependencies\b.*)$`, then `task-execution` for `^(.*task.*execution.*|.*execute.*tasks?.*|.*run.*tasks?.*|task execution\b.*)$`. Overlap SHALL resolve to the first matching bucket. Each classified retained `PhaseState` SHALL add its `totalItems` and `completedItems` to that bucket, so repeated phases sum; unmatched names SHALL be ignored. Every bucket SHALL be emitted, and an absent bucket SHALL be `{totalItems:0, completedItems:0}`. In particular, `dependency-resolution` SHALL be 0/0 when no distinct phase was observed. These values MUST be detached from live mutable progress state.

#### Scenario: Dashboard describes completed build work

- **WHEN** an agent queries DASHBOARD output for a completed build
- **THEN** the output includes total and completed item counts for configuration, dependency resolution, and task execution
- **AND** the counts are a frozen snapshot of the completed build

#### Scenario: Console output describes completed build work

- **WHEN** an agent inspects console-oriented output for a completed build
- **THEN** the output includes the frozen phase counts

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

### Requirement: Build output exposes a configuration-cache report pointer

Build output SHALL expose `configCacheReportPointer: String?`. The server SHALL capture an emitted configuration-cache report path from the authoritative init-script marker and preserve that path verbatim. A null pointer SHALL be correct when the build emits no configuration-cache report. The server MUST NOT open or parse the report.

#### Scenario: Configuration-cache report path is captured

- **WHEN** the init script emits the authoritative configuration-cache report marker with a path
- **THEN** build output includes the verbatim path in `configCacheReportPointer`
- **AND** configuration-cache problems remain available through `query_build kind=PROBLEMS`
- **AND** the server does not open or parse the report

#### Scenario: No configuration-cache report is emitted

- **WHEN** a build has no configuration-cache problems or otherwise emits no configuration-cache report
- **THEN** the authoritative init-script marker is absent
- **AND** `configCacheReportPointer` is absent or null as a correct-null result
- **AND** this result is distinct from failing to capture an emitted marker

