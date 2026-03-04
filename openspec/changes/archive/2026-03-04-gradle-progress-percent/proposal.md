## Why

Currently, Gradle build progress is reported only as text messages without a numerical percentage. This makes it difficult for MCP clients to display a progress bar or accurately estimate the remaining time for a build. Providing a
percentage increases visibility into long-running build tasks.

## What Changes

- **Modified `RunningBuild`**: Add fields to track `totalItems` and `completedItems` for the current build phase (e.g., tasks in execution phase).
- **Modified `GradleProvider`**: Capture `BuildPhaseStartEvent` to reset progress for the new phase, and item-level events (e.g., `TaskFinishEvent`) to update the percentage.
- **Modified `withProgressEmissions`**: Calculate the percentage (0-100%) for the current phase and prefix status messages with the phase name (e.g., `[CONFIGURING]`, `[EXECUTING]`).
- **Sub-operation Progress**: Use `StatusEvent` for granular progress (like downloads) to smooth the progress bar within the current phase.
- **Improved Progress Reporting**: Ensure that progress notifications include the percentage whenever `totalWork` is known.

## Capabilities

### New Capabilities

- `progress-percentage`: Calculates and emits the numerical percentage of build progress based on Gradle Tooling API events.

### Modified Capabilities

- `build-execution`: Enhance the build execution logic to support granular progress tracking and reporting.

## Impact

- **`dev.rnett.gradle.mcp.gradle.build.RunningBuild`**: New internal state for progress tracking.
- **`dev.rnett.gradle.mcp.gradle.GradleProvider`**: Updated `ProgressListener` to handle `StatusEvent`.
- **`dev.rnett.gradle.mcp.tools.McpGradleHelpers`**: `withProgressEmissions` will now calculate and emit percentages.
- **MCP Client**: Will receive `progress` and `total` values in progress notifications, enabling progress bars.
