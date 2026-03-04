# build-execution Specification

## Purpose

TBD - created by archiving change gradle-progress-percent. Update Purpose after archive.

## Requirements

### Requirement: Progress listener in `GradleProvider`

The `GradleProvider` SHALL include a `ProgressListener` that captures `StatusEvent` from the Gradle Tooling API.

#### Scenario: Capturing StatusEvent

- **WHEN** a Gradle build is started via `GradleProvider.runBuild`
- **THEN** the `GradleProvider` SHALL capture `StatusEvent` and update `RunningBuild` accordingly

### Requirement: Emitting progress notification

The system SHALL calculate and emit a numerical percentage for build progress via the MCP client.

#### Scenario: Emitting progress notification with percentage

- **WHEN** a progress event occurs during a build
- **THEN** the system SHALL calculate the percentage and emit a progress notification to the MCP client

