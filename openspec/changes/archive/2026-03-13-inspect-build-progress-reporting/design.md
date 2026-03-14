## Context

Currently, the `inspect_build` tool allows waiting for background builds to complete or reach specific states (via `wait`, `waitFor`, `waitForTask`). However, during this wait period, it does not emit progress notifications. In contrast,
the `gradle` (run) tool reports progress. To improve user and agent experience during potentially long waits, we want `inspect_build` to report the ongoing progress of the build it is waiting for.

## Goals / Non-Goals

**Goals:**

- Enable progress reporting in `inspect_build` during wait periods.
- Re-use existing progress tracking mechanisms (e.g., `ProgressTracker`, `RunningBuild` progress shared flows).
- Provide immediate, periodic feedback to the MCP client while blocking on a background build.

**Non-Goals:**

- Changing how builds are executed or how their progress is initially tracked.
- Modifying the progress reporting format itself; we only want to pipe the existing progress into the `inspect_build` tool's execution context.

## Decisions

- **Listen to `RunningBuild` Progress**: The `inspect_build` tool will obtain the `RunningBuild` instance (if the build is active) and collect from its progress flow during the wait loop.
- **Concurrent Progress Collection**: Since waiting uses coroutines, we will launch a concurrent coroutine within the wait scope that collects progress events and reports them to the MCP client.
- **API Refinement**: The `wait` parameter is renamed to `timeout` for clarity (seconds to wait), and `waitForFinished` is added to explicitly wait for completion even if no other conditions are set.
- **99% Progress Cap**: Progress is capped at 0.99 for running builds to avoid visual "jumping" to 100% before the final cleanup and indexing is complete.

## Risks / Trade-offs

- [Risk] Duplicate progress reports if multiple tools wait on the same build simultaneously.
    - Mitigation: MCP progress is scoped to the specific tool request/token. Each tool invocation has its own progress token.
- [Risk] Waiting on a finished build.
    - Mitigation: Progress collection only occurs if the build is currently `RunningBuild`.
