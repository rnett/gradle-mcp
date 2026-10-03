## MODIFIED Requirements

### Requirement: Task Output Capture Feedback

The system SHALL provide actionable feedback when captureTaskOutput is used but the task's output cannot be found. When captured task output is available, the response SHALL begin with the build identity line `Gradle MCP Build ID: <id>` so the output can be correlated with the build that produced it.

#### Scenario: Display status for running task

- **WHEN** captureTaskOutput is used for a task currently in progress
- **THEN** it SHALL return a message stating the task is still running and its output is not yet available.

#### Scenario: Display status for missing task

- **WHEN** captureTaskOutput is used for a task not found in executed tasks
- **THEN** it SHALL provide a list of tasks that *were* executed.
- **AND** it SHALL suggest if the task is long-running (e.g.,
  un) and thus never "finished" its output.

#### Scenario: Captured output includes the build identity

- **WHEN** captureTaskOutput is set and the build finishes with captured task output
- **THEN** the response SHALL begin with `Gradle MCP Build ID: <id>` followed by a blank line
- **AND** the existing content SHALL follow: the capture-failure warning when present, the truncation hint with the last 100 lines when the output exceeds the limit, or the raw task output otherwise
