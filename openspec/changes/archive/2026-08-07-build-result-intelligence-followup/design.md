## Context

`build-result-intelligence` (archived 2026-08-07) introduced task outcome reasons and task-origin aggregation. Its delta specs set shaped reason strings (`FROM_CACHE: isFromCache=true`, `UP_TO_DATE: isUpToDate=true`, `SKIPPED: ...`, `NO-SOURCE: ...`) and rendered task origins in DASHBOARD and console output. The implemented code and tests in this repo have since moved to a verbatim, prefix-free reason contract and TASKS-only origin rendering. This change reconciles the specs with that implemented reality.

## Goals / Non-Goals

Goals:

- Make `TaskResult.reason` carry the verbatim Gradle skip message for skipped outcomes only.
- Use a null `reason` for every non-skipped outcome, including reused-work outcomes.
- Render task-origin aggregation only in `query_build kind=TASKS`.
- Keep phase counts, config-cache pointer, and PROBLEMS-first routing as archived.

Non-goals:

- No change to the frozen phase-count snapshot or its classification.
- No change to the configuration-cache report pointer contract.
- No new tools or new capability files.

## Decisions

### 1. Null reason for reused and executed outcomes

`FROM_CACHE`, `UP_TO_DATE`, `SUCCESS`, `FAILED`, and `CANCELLED` store a null `reason`. The outcome enum already discriminates these; shaping a string duplicates that and adds parsing burden. In `BuildExecutionService.handleTaskFinish`, the `TaskSuccessResult` branch returns `FROM_CACHE`/`UP_TO_DATE` with a null reason instead of a shaped string.

### 2. Verbatim reason for skipped outcomes

`TaskSkippedResult` keeps the verbatim `skipMessage` as `reason`, with no outcome prefix. `NO_SOURCE` remains a distinct outcome (never collapsed to `SKIPPED`) and still holds the verbatim message (`NO-SOURCE`). TASKS output prints `Reason:` whenever `reason` is non-null.

### 3. Task origins render only in TASKS output

`taskOriginAggregation` is computed on `Build` as before (grouping absent provenance under `_unknown`, conserving total count). Rendering moves to `getTasksOutput` (the `kind=TASKS` path) as a `Task Origins:` section. `toOutputString` (DASHBOARD/console/base) must not include it.

### 4. Scope of contract text

The amended requirement text and scenarios in the delta specs of `build-execution`, `task-provenance-reporting`, and `build-querying` are the source of truth for the follow-up contract.

## Risks / Trade-offs

- Holding the verbatim message without a prefix means `reason: "UP-TO-DATE"` (a skip message) could superficially resemble the `UP_TO_DATE` outcome; the outcome enum remains authoritative and `reason` is only advisory.
- Moving task origins into TASKS output reduces their visibility in dashboard summaries, but agents focused on diagnosis can still query TASKS explicitly.

## Migration Plan

1. Update `BuildExecutionService.handleTaskFinish` to store null reasons for reused and executed outcomes and verbatim reasons for skipped outcomes.
2. Move `Task Origins:` rendering into `getTasksOutput` and remove it from base `toOutputString`.
3. Update focused unit tests to assert the verbatim reason strings and TASKS-only origin rendering.
4. Run targeted tests, then the repository-required broader verification.
