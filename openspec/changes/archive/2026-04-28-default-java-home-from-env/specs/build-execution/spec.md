## ADDED Requirements

### Requirement: Java Home Configuration during Execution

The build execution process SHALL configure the launcher with the resolved Java home before starting the build.

#### Scenario: Configured launcher with Java home

- **WHEN** a build is initiated
- **AND** a Java home path is resolved (either explicitly or via environment)
- **THEN** the system SHALL call `launcher.setJavaHome()` with the resolved path before invoking the build.
