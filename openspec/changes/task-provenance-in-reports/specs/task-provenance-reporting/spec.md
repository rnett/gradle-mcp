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
