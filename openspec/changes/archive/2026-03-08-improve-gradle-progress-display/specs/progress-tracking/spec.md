## ADDED Requirements

### Requirement: Track Active Tasks and Configurations

The system SHALL maintain a real-time list of currently executing tasks and project configurations.

#### Scenario: Task Starts

- **WHEN** a `TaskStartEvent` is received
- **THEN** the task path SHALL be added to the active operations list

#### Scenario: Task Finishes

- **WHEN** a `TaskFinishEvent` is received
- **THEN** the task path SHALL be removed from the active operations list

#### Scenario: Configuration Starts

- **WHEN** a `ProjectConfigurationStartEvent` is received
- **THEN** the project path SHALL be added to the active operations list

#### Scenario: Configuration Finishes

- **WHEN** a `ProjectConfigurationFinishEvent` is received
- **THEN** the project path SHALL be removed from the active operations list

### Requirement: Descriptive Progress Messages

The system SHALL generate progress messages that clearly indicate what work is currently in progress, prioritizing active tasks over recently finished ones.

#### Scenario: Single Task in Progress

- **WHEN** one task is in the active operations list
- **THEN** the progress message SHALL be "Executing task: :taskPath"

#### Scenario: Multiple Tasks in Progress

- **WHEN** multiple tasks are in the active operations list
- **THEN** the progress message SHALL use the **first-started** task as the lead (e.g., "Executing tasks: :task1 and 2 others")

#### Scenario: No Tasks in Progress

- **WHEN** the active operations list is empty and at least one task has finished
- **THEN** the progress message SHALL be "Finished task: :lastTaskPath"

### Requirement: Phase-Aware Progress

The system SHALL include the current build phase (e.g., [CONFIGURING], [EXECUTING]) as a prefix in progress messages when appropriate.

#### Scenario: Configuration Phase

- **WHEN** the build is in the `CONFIGURE_BUILD` phase
- **THEN** progress messages SHALL be prefixed with "[CONFIGURING]"

#### Scenario: Execution Phase

- **WHEN** the build is in the `RUN_MAIN_TASKS` phase
- **THEN** progress messages SHALL be prefixed with "[EXECUTING]"
