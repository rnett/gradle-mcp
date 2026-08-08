## Why

Gradle MCP already derives and tracks information that AI agents need to explain build results, but parts of that information are discarded or collapsed before reaching the tool surface. As a result, agents can fall back to low-signal file-reading loops after failures and cannot reliably explain why tasks were skipped or cached, where build work occurred, or whether a configuration-cache report is available.

This change completes the existing build-result surface and teaches the shipped `using-gradle` skill to route agents through it. The focus is agent-oriented diagnosis: query structured problems first on failure, expose already-known task and phase details, and capture the cheapest useful configuration-cache signal without building human-report tooling.

## What Changes

- Route failed and low-signal build diagnosis to `query_build kind=PROBLEMS` before file-read investigation.
- Preserve the already-derived task outcome reason in `TaskResult`, including every skipped outcome via `TaskSkippedResult.skipMessage`, while preserving `NO_SOURCE` as a distinct outcome that is never remapped to `SKIPPED`; render each non-null reason with outcome and provenance in `query_build kind=TASKS`.
- Expose frozen phase counts from retained completed-phase history in the normalized configuration, dependency-resolution, and task-execution buckets, plus task counts aggregated by origin plugin with absent provenance grouped under `_unknown`.
- Emit a nullable, verbatim configuration-cache report path from an authoritative init-script marker when Gradle produces a report, while leaving configuration-cache problems in the generic `PROBLEMS` stream and treating no emitted report as correct-null.
- Add `using-gradle` routes for JDK and daemon questions and for multi-project, composite-build, and convention-plugin ownership questions.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `build-querying`: Complete `query_build` output with task reasons, frozen phase counts, task-origin aggregation, PROBLEMS-first triage, and a configuration-cache report pointer.
- `task-provenance-reporting`: Extend each task provenance result with its outcome reason.
- `build-execution`: Preserve the task outcome reason already derived when task execution finishes.
- `build-monitoring-progress`: Expose tracked build-phase totals and completions as a frozen result snapshot.
- `using-gradle`: Route agents through structured problem, task-reason, phase, configuration-cache, build-environment, and project-graph diagnostics.

## Impact

The implementation affects the build result models, task-finish handling, progress snapshotting, build output capture, `query_build` rendering, focused tests, generated tool documentation, and the shipped `using-gradle` skill. The new result fields are additive and nullable where absence is meaningful. `NO_SOURCE` remains a preserved outcome and is not remapped to `SKIPPED`. No standalone environment or project-graph tools are introduced, and configuration-cache reports are never parsed.
