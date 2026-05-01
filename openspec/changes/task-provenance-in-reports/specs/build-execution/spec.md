## ADDED Requirements

### Requirement: Task provenance extraction from TaskOperationDescriptor

The system SHALL extract task provenance information from the Tooling API's `TaskOperationDescriptor` during task execution.

#### Scenario: Extract provenance from TaskOperationDescriptor

- **WHEN** a task finishes via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns a `BinaryPluginIdentifier` with a non-null `getPluginId()`
- **THEN** the system SHALL extract the plugin ID as provenance from the descriptor
- **AND** SHALL store it in the `TaskResult` model

#### Scenario: No provenance when API unavailable

- **WHEN** a task finishes via the Tooling API
- **AND** `TaskOperationDescriptor.getOriginPlugin()` returns null or a `ScriptPluginIdentifier` (no plugin ID)
- **THEN** the system SHALL NOT attempt to extract provenance
