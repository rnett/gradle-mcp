## Context

Currently, when a Gradle build runs and gets cancelled (e.g., via Ctrl+C, or a stop action from the MCP server), the system output may just indicate the build failed or was interrupted. Tests that were currently executing are not clearly
reported, leaving the user guessing which tests might have been the cause of a hang or which ones were left incomplete.

## Goals / Non-Goals

**Goals:**

- Identify tests that are in progress when a Gradle build terminates or is cancelled.
- Display a clear list of these in-progress tests in the MCP output or build report.

**Non-Goals:**

- Showing live updating progress bars for tests.
- Re-running the cancelled tests automatically.

## Decisions

- **Test Tracking:** `TestCollector` (in `GradleProvider.kt`) tracks active tests by handling `TestStartEvent` (added to an `inProgress` map with start time) and removing them on `TestFinishEvent`.
- **Handling Skipped-but-Started Tests:** Gradle sometimes reports interrupted tests as `SKIPPED` in the `TestFinishEvent`. The implementation now checks if a test was in the `inProgress` map when a `SKIPPED` result is received; if so, it
  is treated as `CANCELLED` rather than a standard skip.
- **Reporting Mechanism:**
    - `TestOutcome` enum (in `Models.kt`) updated with `CANCELLED` and `IN_PROGRESS`.
    - `TestResults` (in `Models.kt`) and `TestCollector.Results` (in `GradleProvider.kt`) updated to include a `cancelled` set of tests.
    - `RunningBuild.testResults` getter maps these in-progress tests to `IN_PROGRESS` if the build is still running, or `CANCELLED` if it has stopped.
    - `Build.toOutputString()` (in `GradleOutputs.kt`) and the build lookup tool (in `GradleBuildLookupTools.kt`) updated to display these statuses appropriately.
    - For running builds, it shows "In Progress: N". For finished builds, it shows "Cancelled: N" and lists them similarly to failed tests.

## Risks / Trade-offs

- **Memory Leak:** If finish events are missed, the active tests map could grow. Mitigation: Ensure tracking is tied to a specific build execution and cleared after the build completes.
- **Noise:** A large number of concurrently running tests might flood the output. Mitigation: Limit the displayed in-progress tests to a reasonable number (e.g., top 10) and indicate "and N others".