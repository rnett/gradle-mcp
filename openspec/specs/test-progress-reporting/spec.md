## ADDED Requirements

### Requirement: Test Execution Statistics Tracking

The system SHALL track the number of passed, failed, and skipped tests in real-time during a build execution.

#### Scenario: Track multiple outcomes

- **WHEN** multiple tests finish with different results (passed, failed, skipped)
- **THEN** the system increments the corresponding counters correctly

### Requirement: Test Progress in Build Status Message

The system SHALL include test execution statistics in the build progress message whenever at least one test has been executed or is in progress.

#### Scenario: Display test counts when active

- **WHEN** a test task is running and some tests have finished
- **THEN** the progress message includes a string like `(10 passed, 2 failed, 1 skipped)`

### Requirement: Real-time Progress Updates for Test Events

The system SHALL emit a new build progress notification immediately when a test finishing event is received from the build tool.

#### Scenario: Notification on test completion

- **WHEN** a single test finishes execution
- **THEN** the system emits a progress notification with updated counts, even if the current task hasn't finished.
