## Context

Gradle builds currently report their status as textual log lines without a numerical percentage. This provides limited information to the user and the MCP client regarding the overall progress of long-running tasks. The Gradle Tooling API
emits `BuildPhaseStartEvent` with `BuildPhaseOperationDescriptor`, which provides `getBuildItemsCount()` for the current phase (e.g., number of tasks in the execution phase). We can combine this with `TaskFinishEvent` (and other item-level
events) to calculate a percentage that matches the Gradle CLI's "X% EXECUTING" behavior.

## Goals / Non-Goals

**Goals:**

- **Accurate Build Progress**: Calculate a 0-100% percentage for the current build phase based on completed items vs. total items in that phase.
- **Phase Context**: Prefix the status message with the active phase (e.g., `[CONFIGURING]`, `[EXECUTING]`) to avoid confusion when the bar resets between phases.
- **Smooth Progress Bar**: Incorporate `StatusEvent` (e.g., download progress) to move the progress bar smoothly between item-level completions within each phase.

## Decisions

- **Decision 1: Phase-Based 0-100% Scale**: Instead of a global build scale, each build phase will report 0-100%.
    - **Phase Mappings (Internal to Display)**:
        - `CONFIGURE_ROOT_BUILD`, `CONFIGURE_BUILD` -> `[CONFIGURING]`
        - `RUN_MAIN_TASKS`, `RUN_WORK` -> `[EXECUTING]`
    - **Rationale**: Many MCP clients display a discrete progress bar with sub-text. Resetting the bar at the start of a phase, while prefixing the message with a new phase label, is clear and matches how IDEs/CLIs often handle multi-stage
      processes.
- **Decision 2: Sub-item Smoothing**: Use `StatusEvent.getProgress()` and `getTotal()` to interpolate the percentage between two items within the 0-100% phase range.
    - **Formula**: `PhaseProgress = (CompletedItems + (CurrentItemProgress / CurrentItemTotal)) / TotalItemsInPhase * 100`
    - **Fallback**: If `CurrentItemTotal` is unknown, the bar stays at the `CompletedItems` mark until the item finishes.
- **Decision 3: Use `BuildPhaseOperationDescriptor` for Totals**: Capture the `buildItemsCount` from `BuildPhaseStartEvent` to establish the denominator for the new phase.
- **Decision 4: Track Item Completion**: Increment the completed count in `RunningBuild` upon receiving `TaskFinishEvent`, `ProjectConfigurationFinishEvent`, or `TransformFinishEvent`.
- **Decision 5: Resetting on Phase Change**: Reset the `completedItems` count to zero whenever a new `BuildPhaseStartEvent` is received.
- **Decision 6: Lambda-Based API Integration**: Update `GradleProvider` methods to accept an optional `progressHandler: ((progress: Double, total: Double?, message: String?) -> Unit)?`, mirroring the existing `stdoutLineHandler`.
- **Decision 7: Structured `withProgressEmissions`**: Refactor the `withProgressEmissions` utility in `McpGradleHelpers.kt` to maintain a sampled `ProgressState` that combines numerical progress and log messages into a single MCP
  notification stream.

## Risks / Trade-offs

- **[Risk] Status Event Overhead** → **Mitigation**: Status events are only emitted for certain operations (like downloads). The impact on performance should be minimal.
- **[Risk] Uncertain Totals** → **Mitigation**: If `getTotal()` returns -1, we'll gracefully handle it and only emit textual updates without a percentage.
- **[Risk] Confusing Mixed Progress** → **Mitigation**: We'll ensure that the message associated with the percentage notification provides context for what is being measured.
