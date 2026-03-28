## ADDED Requirements

### Requirement: Build Summary Error Context

When a build ID is provided to `inspect_build` in `summary` mode, the response SHALL include the first few lines of actual failure messages if the build has errors.

#### Scenario: Display recent error context in summary

- **WHEN** `inspect_build(buildId="...", mode="summary")` is called for a build with failures
- **THEN** the output SHALL include a "Recent Error Context" section.
- **AND** it SHALL show the message and top lines of the description for up to 3 failures.

### Requirement: Active Operations Visibility

The build summary output SHALL explicitly list currently running tasks if the build is still in progress.

#### Scenario: Display active tasks in summary

- **WHEN** `inspect_build(buildId="...", mode="summary")` is called for a running build
- **THEN** the output SHALL include an "Active Tasks" list showing the paths of tasks currently being executed.
