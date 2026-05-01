## ADDED Requirements

### Requirement: Mandatory Console Tail

The `wait_build` tool SHALL always return a tail of the console output upon completion, regardless of the wait condition.

#### Scenario: Wait for task complete

- **WHEN** `wait_build(waitForTask=":assemble")` completes
- **THEN** system returns the status and the last 5 lines of the build console

### Requirement: Automatic Blocking Default

If no specific wait condition (regex or task) is provided to `wait_build`, it SHALL default to waiting for the build to finish.

#### Scenario: Implicit finish wait

- **WHEN** user calls `wait_build(buildId="ID")` without `waitForTask` or `waitForRegex`
- **THEN** system blocks until the build finishes
