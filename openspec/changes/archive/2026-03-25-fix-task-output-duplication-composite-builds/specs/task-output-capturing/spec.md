## ADDED Requirements

### Requirement: Task output is not duplicated in composite builds

When the `task-out` init script is applied to a composite build (a project with one or more included builds), each line of task output SHALL appear exactly once in the captured output, regardless of the number of included builds.

#### Scenario: Output deduplication with one included build

- **WHEN** the `task-out` init script is used with a Gradle project that has one included build (e.g., buildSrc or a standalone included build)
- **THEN** each line of task output SHALL appear exactly once in `consoleOutput` and `getTaskOutput()`

#### Scenario: Output deduplication with multiple included builds

- **WHEN** the `task-out` init script is used with a Gradle project that has N included builds (N > 1)
- **THEN** each line of task output SHALL appear exactly once — not N+1 times
