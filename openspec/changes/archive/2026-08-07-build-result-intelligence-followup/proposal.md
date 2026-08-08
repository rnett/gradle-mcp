## Why

The initial `build-result-intelligence` change stored explanatory reason text for reused work (`FROM_CACHE: isFromCache=true`, `UP_TO_DATE: isUpToDate=true`) and prefixed every skipped-task reason with its outcome (`SKIPPED: ...`, `NO-SOURCE: ...`), and it rendered task origins in DASHBOARD and console output. In practice these shaped strings duplicated information the agent already sees from the outcome enum, made reasons noisier, and surfaced task-origin aggregation in views where it is rarely the agent's focus. The follow-up amends the contract so reasons are held verbatim only for skipped tasks and task origins render only in `query_build kind=TASKS`.

## What Changes

- Store a null `reason` for `FROM_CACHE`, `UP_TO_DATE`, `SUCCESS`, `FAILED`, and `CANCELLED` task results, since outcome already conveys this without a shaped string.
- Preserve the verbatim Gradle skip message as `reason` for skipped tasks, with no `SKIPPED:` or `NO-SOURCE:` prefix and no `FROM_CACHE:`/`UP_TO_DATE:` prefix.
- Keep `NO_SOURCE` distinct from `SKIPPED`, never collapsing it, while still holding the verbatim message.
- Render task-origin aggregation (`Task Origins:`) only in `query_build kind=TASKS` output, and never in DASHBOARD, CONSOLE, or base build-result rendering.
- Keep the frozen phase-count snapshot and configuration-cache report pointer unchanged from the prior change.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `build-execution`: `TaskResult.reason` is null for cache/up-to-date/success/failure/cancelled outcomes and holds the verbatim skip message (no prefix) for `SKIPPED` and `NO_SOURCE`.
- `task-provenance-reporting`: The surfaced reason policy follows the same verbatim, prefix-free rule.
- `build-querying`: `kind=TASKS` output includes the verbatim reason and renders `Task Origins:`; DASHBOARD, CONSOLE, and base output omit task origins.

## Impact

This is a purely contract-level refinement of behavior introduced by `build-result-intelligence`; it tightens the reason strings to be verbatim and prefix-free, and moves task-origin rendering into TASKS-only output. Implementation changes are in the task-result reason population, the TASKS/`getTasksOutput` rendering, and the task-origin aggregation exposure, with focused unit tests updated to assert the new verbatim strings and TASKS-only rendering.
