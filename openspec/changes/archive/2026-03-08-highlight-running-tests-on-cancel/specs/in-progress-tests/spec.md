## ADDED Requirements

### Requirement: Track running tests

The system SHALL track the state of test operations during a Gradle build to identify tests that have started but not yet finished.

#### Scenario: Test starts

- **WHEN** a test execution begins
- **THEN** the test is added to the in-progress tracking collection

#### Scenario: Test finishes

- **WHEN** a test execution completes (success, failure, or skipped)
- **THEN** the test is removed from the in-progress tracking collection

### Requirement: Report in-progress tests

The system SHALL display tests that were currently running when inspecting the build status.

#### Scenario: Build running with tests

- **WHEN** a build is in progress and there are tests in the tracking collection
- **THEN** inspecting the build status shows an "In Progress" test count

#### Scenario: Build finished with unfinished tests

- **WHEN** a build completes (cancelled or failed) and there are still tests in the tracking collection
- **THEN** inspecting the build status shows these as "Cancelled" tests and lists them similarly to failed tests