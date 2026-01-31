# Background Gradle Builds Design

This document outlines the design for running Gradle builds in the background within the Gradle MCP server.

## Goals

- Allow users to start Gradle builds without waiting for them to complete.
- Provide visibility into the status and progress of background builds.
- Enable users to stop/cancel running background builds.
- Retain build results for a period after completion.

## Architecture

### `BackgroundBuildManager`

A new component responsible for managing the lifecycle of background builds.

- **State Management**: Keeps track of running and recently completed builds.
- **Build Handle**: Each background build is identified by a `BuildId`.
- **Cancellation**: Stores the `CancellationTokenSource` from Gradle Tooling API to allow stopping builds.
- **Status Tracking**: Tracks the current state of the build (RUNNING, SUCCESSFUL, FAILED, CANCELLED).
- **Log Buffering**: Buffers console output so it can be retrieved while the build is running.

### Build Statuses

- `RUNNING`: Build is currently being executed by Gradle.
- `SUCCESSFUL`: Build completed successfully.
- `FAILED`: Build failed with an error.
- `CANCELLED`: Build was explicitly stopped by the user.

### Tooling API Integration

The `DefaultGradleProvider` will be updated to support asynchronous execution using `LongRunningOperation.run(ResultHandler)`.

## MCP Tools

### `background_build_run_command`

- Starts a Gradle command in the background.
- Returns the `BuildId` immediately.
- Parameters: Same as `run_gradle_command`.

### `background_build_run_tests`

- Starts a Gradle test task in the background.
- Returns the `BuildId` immediately.
- Parameters: Same as `run_tests_with_gradle`.

### `background_build_run_many_tests`

- Starts multiple Gradle test tasks in the background.
- Returns the `BuildId` immediately.
- Parameters: Same as `run_many_test_tasks_with_gradle`.

### `background_build_list`

- Returns a list of all active and recently completed background builds.
- Includes `BuildId`, command line, status, and start time.

### `background_build_get_status`

- Parameter: `buildId: BuildId`.
- Returns detailed status:
    - Current state.
    - Progress information.
    - Tail of the console output.
    - Final `BuildResult` if completed.
- The final build result and any statuses will be string formatted like the existing Gradle command tools.

### `background_build_stop`

- Parameter: `buildId: BuildId`.
- Signals Gradle to stop the build via `CancellationToken`.

## Implementation Plan

### Phase 1: Core Infrastructure

1. Implement `BackgroundBuildManager` with basic tracking and cancellation support.
2. Update `DefaultGradleProvider` to handle asynchronous operations and report status updates to the manager. Use coroutines for this as much as possible, and make sure the architecture is clean.

### Phase 2: Tooling Implementation

1. Add `background_build_run_command`, `background_build_run_tests`, and `background_build_run_many_tests` tools to `GradleExecutionTools`.
2. Add `background_build_list`, `background_build_get_status`, and `background_build_stop` tools to `GradleExecutionTools`.

## Phase 3: Recent builds updates

1. Update the recent builds tool to include builds that are currently running in the background with a descriptive status (and a mention in the description of how to get their status).
2. Ensure that background builds can have their info queried through the lookup tools once they are finished.

### Phase 4: Robustness and UX

1. Ensure log buffering is efficient and doesn't leak memory.
2. Ensure string formatting of build results and statuses matches existing tools.
3. Add tests for background builds, status polling, and cancellation.
4. Update documentation with background build usage.
