## ADDED Requirements

### Requirement: Environment-Based Java Home Resolution

The system SHALL attempt to resolve the Java home directory from the environment if it is not explicitly provided in the build arguments.

#### Scenario: JAVA_HOME present in environment

- **WHEN** a build is started without an explicit `javaHome`
- **AND** `JAVA_HOME` is present in the resolved environment (inherited or shell)
- **THEN** the system SHALL use the value of `JAVA_HOME` as the Java home for the Gradle launcher.

#### Scenario: JAVA_HOME absent from environment

- **WHEN** a build is started without an explicit `javaHome`
- **AND** `JAVA_HOME` is not present in the resolved environment
- **THEN** the system SHALL NOT set a specific Java home on the Gradle launcher, allowing the Tooling API to use its default behavior.

#### Scenario: Explicit javaHome provided

- **WHEN** a build is started with an explicit `javaHome`
- **THEN** the system SHALL use the provided `javaHome`, ignoring any `JAVA_HOME` variable in the environment.
