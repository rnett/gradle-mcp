# Capability: task-provenance-reporting

## Purpose

Defines how the MCP server surfaces Gradle task provenance information — which plugin registered a task — in task queries and build reports via the Tooling API.
## Requirements
### Requirement: Parse task provenance from Tooling API

The system SHALL obtain task provenance information from the Tooling API's `TaskOperationDescriptor` when available.

#### Scenario: Extract plugin provenance from TaskOperationDescriptor

- **WHEN** a task is executed via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns a `BinaryPluginIdentifier` with a non-null `getPluginId()`
- **THEN** the system SHALL use the returned plugin ID as the task's provenance

#### Scenario: Handle missing provenance gracefully

- **WHEN** a task is executed via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns null or a `ScriptPluginIdentifier` (no plugin ID), or Gradle version < 9.5
- **THEN** the system SHALL report provenance as absent/null without error

### Requirement: Store provenance in task results

The system SHALL store parsed provenance information as a structured field in task result data.

#### Scenario: Provenance stored in TaskResult

- **WHEN** a task finishes and provenance is extracted
- **THEN** the provenance SHALL be stored in the `TaskResult` model as an optional `provenance` field

### Requirement: Display provenance in task details

The system SHALL display provenance information in task detail output when available.

#### Scenario: Provenance shown in task details

- **WHEN** a user queries task details via `query_build` with a task path
- **AND** the task has provenance information from `TaskOperationDescriptor.getOriginPlugin()` (cast to `BinaryPluginIdentifier` → `getPluginId()`)
- **THEN** the output SHALL include a "Provenance" line showing the plugin ID

#### Scenario: Provenance omitted when absent

- **WHEN** a user queries task details via `query_build` with a task path
- **AND** the task does not have provenance information
- **THEN** the output SHALL NOT include a "Provenance" line

### Requirement: Support --provenance for task listing

The system SHALL support passing the `--provenance` flag to the `tasks` report via the `gradle` tool.

#### Scenario: --provenance flag passed to tasks report

- **WHEN** a user runs the `gradle` tool with `tasks --provenance` in the command line
- **THEN** the system SHALL pass the `--provenance` flag through to the Gradle CLI
- **AND** the output SHALL include provenance information for each task

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

