# Capability: build-execution

## Purpose

Specifies how the GradleProvider captures Gradle Tooling API progress events and emits MCP progress notifications with numerical percentages.

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

### Requirement: Java Home Configuration during Execution

The build execution process SHALL configure the launcher with the resolved Java home before starting the build.

#### Scenario: Configured launcher with Java home

- **WHEN** a build is initiated
- **AND** a Java home path is resolved (either explicitly or via environment)
- **THEN** the system SHALL call `launcher.setJavaHome()` with the resolved path before invoking the build.
