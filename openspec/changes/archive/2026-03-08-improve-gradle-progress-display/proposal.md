## Why

Currently, Gradle build progress notifications show the last event received, which often results in displaying "Finished task: :path" for long periods if there's a delay before the next task starts. In parallel builds, only one task is
shown at a time, hiding other active work.

## What Changes

- Track active tasks and project configurations in `RunningBuild`.
- Update progress messages to prioritize in-progress work over finished work.
- Support displaying multiple in-progress tasks (e.g., ":task1, :task2, and 2 others").
- Ensure that if no work is in progress, the last finished task is still shown but labeled as such.

## Capabilities

### New Capabilities

- `progress-tracking`: Enhanced tracking of concurrent Gradle operations (tasks, configuration) to provide more accurate and descriptive progress updates.

### Modified Capabilities

- (None)

## Impact

- `RunningBuild`: New state for tracking active operations.
- `DefaultGradleProvider`: Updated progress listeners to manage active operation state.
- `McpGradleHelpers`: Updated `doBuild` to use the enhanced progress information.
