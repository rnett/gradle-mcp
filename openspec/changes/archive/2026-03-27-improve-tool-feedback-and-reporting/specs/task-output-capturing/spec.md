## ADDED Requirements

### Requirement: Task Output Capture Feedback

The system SHALL provide actionable feedback when `captureTaskOutput` is used but the task's output cannot be found.

#### Scenario: Display status for running task

- **WHEN** `captureTaskOutput` is used for a task currently in progress
- **THEN** it SHALL return a message stating the task is still running and its output is not yet available.

#### Scenario: Display status for missing task

- **WHEN** `captureTaskOutput` is used for a task not found in executed tasks
- **THEN** it SHALL provide a list of tasks that *were* executed.
- **AND** it SHALL suggest if the task is long-running (e.g., `run`) and thus never "finished" its output.

### Requirement: Capture Timeout Warnings

The system SHALL emit a progress warning if a build with `captureTaskOutput` takes longer than a threshold (e.g., 10 seconds).

#### Scenario: Display warning for long builds

- **WHEN** a build with `captureTaskOutput` has been running for 10 seconds without finishing
- **THEN** the progress reporter SHALL report a message explaining that `captureTaskOutput` only returns results AFTER the build finishes.
