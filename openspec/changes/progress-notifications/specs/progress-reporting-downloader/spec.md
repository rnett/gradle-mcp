## ADDED Requirements

### Requirement: Multi-stage progress pattern

The system SHALL follow the Gradle-style multi-stage progress pattern for documentation tasks (Download, Extract, Index).

#### Scenario: Progress resets between stages

- **WHEN** the system finishes the download stage and starts extraction
- **THEN** the progress SHALL reset to 0% and the status message MUST be updated with the \`[EXTRACTING]\` prefix

### Requirement: Download progress reporting

The \`DistributionDownloaderService\` SHALL report download progress using a generic callback.

#### Scenario: Real-time download feedback with prefix

- **WHEN** a Gradle distribution is being downloaded
- **THEN** the system MUST emit progress notifications with the \`[DOWNLOADING]\` prefix and percentage based on bytes

### Requirement: Progress update lambda

Services SHALL accept an optional progress lambda to maintain decoupling from the MCP layer.

#### Scenario: Tool bridges service updates to MCP

- **WHEN** a service emits a progress update via its lambda
- **THEN** the tool layer MUST catch this update and propagate it as an MCP \`ProgressNotification\`
