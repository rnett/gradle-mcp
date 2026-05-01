## Current State

As of the latest codebase audit, **no provenance-related code has been implemented yet**. The following reflects the actual current state of the relevant code:

### Data Model (`Models.kt`)

- `TaskResult` has fields: `path`, `outcome`, `duration`, `consoleOutput`
- **No `provenance` field exists** — task 1.1 is pending

### Build Execution (`BuildExecutionService.kt`)

- `handleTaskFinish()` extracts `taskPath`, `outcome`, `duration` from `TaskOperationDescriptor`
- **Does NOT call `TaskOperationDescriptor.getOriginPlugin()`** — task 2.1 is pending
- `RunningBuild.addTaskResult()` creates `TaskResult(taskPath, outcome, duration, consoleOutput)` — no provenance parameter

### Task Result Preservation (`RunningBuild.kt`)

- `RefFinishedBuild` maps task results with `result.copy(consoleOutput = ...)`
- **No provenance field to copy** — task 2.2 is pending (depends on 1.1 and 2.1)

### Task Display (`GradleBuildLookupTools.kt`)

- `getTasksOutput()` shows: Task path, Outcome, Duration, Tests, Output
- **Does NOT show provenance** — task 3.1 is pending

### Gradle CLI Passthrough (`GradleExecutionTools.kt`)

- `GradleExecuteArgs` has `commandLine`, `background`, `stopBuildId`, `captureTaskOutput`, `invocationArguments`
- **No explicit `--provenance` support** — task 4.1 is pending

### Zero occurrences

- No references to `provenance`, `getPluginId`, or `--provenance` exist anywhere in the source code

## Context

Gradle 9.5.0 introduces task provenance information. When a task is registered, Gradle can report which plugin registered it. The `tasks --provenance` command also displays provenance. The underlying Tooling API method
`TaskOperationDescriptor.getOriginPlugin()` has existed since Gradle 5.1, but the provenance data it returns is only populated meaningfully in Gradle 9.5+.

The MCP server currently:

- Captures task outcomes via Tooling API `TaskFinishEvent` but does not extract provenance from the task descriptor
- Supports `help --task` via the Tooling API `Help` model, which returns rendered text
- Does not surface provenance information separately

The provenance information from Gradle 9.5+ is available through the Tooling API's `TaskOperationDescriptor`, which exposes a `getOriginPlugin()` method returning a `PluginIdentifier`. For binary plugins, this can be cast to
`BinaryPluginIdentifier` to access `getPluginId()`. For script plugins, the returned `ScriptPluginIdentifier` does not have a plugin ID.

## Goals / Non-Goals

**Goals:**

- Surface task provenance as a structured field in task results when available via the Tooling API
- Add provenance display to the `query_build` tool's task output
- Support the `--provenance` option for task listing via the `gradle` tool

**Non-Goals:**

- Modifying Gradle's own provenance output format
- Parsing rendered output (failure messages, help text) for provenance — we only use Tooling API support
- Backporting provenance to older Gradle versions (feature requires Gradle 9.5+)
- Adding provenance to test failure messages (Gradle intentionally omits it for verification failures)

## Decisions

### Decision 1: Extract provenance from Tooling API TaskOperationDescriptor

The Tooling API's `TaskOperationDescriptor` provides task identity information. In Gradle 9.5+, the `getOriginPlugin()` method returns a `PluginIdentifier` that may be a `BinaryPluginIdentifier` with a `getPluginId()` method returning the
plugin ID that registered the task.

**Approach**: Use `TaskOperationDescriptor.getOriginPlugin()` to obtain the `PluginIdentifier`, then cast to `BinaryPluginIdentifier` and call `getPluginId()` to obtain provenance directly from the Tooling API. The Tooling API handles
version compatibility gracefully — `getOriginPlugin()` exists since Gradle 5.1 and returns null when provenance is not available. If `getOriginPlugin()` returns null or a `ScriptPluginIdentifier` (which has no plugin ID), provenance will be
absent.

This avoids any rendered output parsing, which is fragile and version-dependent.

**Alternatives considered**:

- Regex parsing of failure messages — rejected because we do not want to parse rendered output
- Using the `Help` model for task queries — only works for `help --task`, not for task descriptors

### Decision 2: Add provenance as an optional field to TaskResult

Add a `provenance: String?` field to `TaskResult` in `Models.kt`. This keeps the data model clean and allows consumers to check for provenance without breaking changes.

### Decision 3: Extract provenance in BuildExecutionService via TaskOperationDescriptor

When a task finishes, extract provenance from `TaskOperationDescriptor.getOriginPlugin()` (if available), cast to `BinaryPluginIdentifier`, and call `getPluginId()` to get the plugin ID. Store it in the `TaskResult`. This works for all task
outcomes, not just failures.

### Decision 4: Surface provenance in query_build task output

Add a "Provenance" line to the task details output in `GradleBuildLookupTools.kt` when provenance information is available. This applies to all tasks, not just failed ones.

### Decision 5: Support --provenance in the gradle tool

Add `--provenance` as a supported CLI argument that can be passed through to the `tasks` report. This is a simple passthrough — the Gradle CLI handles the rendering.

## Risks / Trade-offs

- **Gradle version dependency**: Provenance requires Gradle 9.5+. Older versions will not include provenance. The code must gracefully handle null/absent provenance.
- **API availability**: `TaskOperationDescriptor.getOriginPlugin()` exists since Gradle 5.1. If it returns null or a `ScriptPluginIdentifier` (which has no `getPluginId()`), provenance will be absent. No workaround via rendered output
  parsing.
- **Limited scope**: Provenance is only available for binary plugin-registered tasks via `BinaryPluginIdentifier.getPluginId()`. Build file and settings file provenance use `ScriptPluginIdentifier` which does not expose a plugin ID, so they
  are not surfaced.
