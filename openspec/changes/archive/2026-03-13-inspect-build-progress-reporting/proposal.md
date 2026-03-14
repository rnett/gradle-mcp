## Why

Currently, when the `inspect_build` tool waits on a background build, it does not report the progress of that build, leaving the user and agent in the dark during long waits. We want `inspect_build` to report the progress of the builds it's
waiting on, just as the `gradle` execution tool does, to provide better visibility.

## What Changes

- The `inspect_build` tool's wait mechanism will be modified to periodically report progress.
- This will likely involve tapping into the progress shared flow in `RunningBuild` or utilizing the progress tracker.
- Progress notifications will be sent to the MCP client while waiting.

## Capabilities

### New Capabilities

- `build-monitoring-progress`: The ability for the `inspect_build` tool to report real-time progress to the client while waiting for an active background build to complete or reach a target state.

### Modified Capabilities
- 

## Impact

- The `inspect_build` tool implementation (specifically the wait logic).
- `RunningBuild` or `ProgressTracker` will be leveraged to surface progress events.
- MCP client interaction (more progress notifications will be sent during tool execution).
