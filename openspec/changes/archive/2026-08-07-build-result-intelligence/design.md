## Context

Gradle MCP serves AI agents rather than human report readers. For these consumers, a useful build-result surface must make the next diagnostic action explicit and retain enough structured context to answer why work did or did not run and where build costs originated.

The current implementation already has much of the required information. `BuildExecutionService.handleTaskFinish` receives cache, up-to-date, and skipped state, including `TaskSkippedResult.skipMessage`, but `TaskResult` does not retain an outcome reason. `BuildProgressTracker` tracks phase names, total items, and completed items in a private active stack, then drops each `PhaseState` when the phase finishes before the completed build is created. Task provenance is already available, and configuration-cache problems already enter the generic problem aggregation stream exposed by `query_build kind=PROBLEMS`.

The remaining gaps are therefore surface completion and routing. Failed or low-signal builds should lead agents to structured problems instead of file-read loops. Successful builds should expose task reasons, phase counts, and task-origin attribution. Configuration-cache diagnosis needs only a pointer to Gradle's report, not a new parser or human-oriented report system.

## Goals / Non-Goals

Goals:

- Preserve and expose already-derived task outcome reasons alongside task outcomes and provenance.
- Expose phase totals and completions, plus task counts grouped by origin plugin, as a frozen completed-build snapshot.
- Route failed-build diagnosis through `query_build kind=PROBLEMS` first.
- Capture and expose a verbatim configuration-cache report path from an authoritative init-script marker, with console interception as fallback-only.
- Teach the `using-gradle` skill how to answer build-environment and project-ownership questions with existing Gradle tasks and commands.

Non-goals:

- Parse configuration-cache reports.
- Add standalone build-environment or project-graph tools.
- Add a performance timer or profile parser.
- Verify publication behavior.
- Recreate Develocity features.
- Build human-oriented report tools.

## Decisions

### 1. Bundle result-surface completion and skill routing

Task reasons, phase counts, configuration-cache pointers, PROBLEMS-first routing, and the two diagnostic routers ship in one proposal. This avoids concurrent changes to `GradleBuildLookupTools.kt` and the `using-gradle` skill while presenting agents with one coherent build-result workflow.

### 2. Represent the task outcome reason as one nullable string

`TaskResult` gains `reason: String?` rather than a structured reason hierarchy. The reason preserves the already-derived explanation without introducing a parallel outcome model.

`TaskResult.outcome` remains one of `SUCCESS`, `FAILED`, `SKIPPED`, `UP_TO_DATE`, `FROM_CACHE`, `NO_SOURCE`, `CANCELLED`, or `IN_PROGRESS`. `FROM_CACHE` uses `FROM_CACHE: isFromCache=true`, and `UP_TO_DATE` uses `UP_TO_DATE: isUpToDate=true`. A `TaskSkippedResult` whose `skipMessage` triggers the no-source mapping in `BuildExecutionService:283` retains the `NO_SOURCE` outcome and uses `NO-SOURCE: <skipMessage verbatim>`; it is never collapsed to `SKIPPED`. Every other `TaskSkippedResult` uses the `SKIPPED` outcome and `SKIPPED: <skipMessage verbatim>`. `SUCCESS`, `FAILED`, and `CANCELLED` have a null reason.

### 3. Publish phase counts as a frozen snapshot

Classification trims each retained phase name, matches case-insensitively, and applies one top-down first-match precedence: `configuration` for `^(CONFIGURATION|configure\b.*|configuration\b.*|project configuration\b.*)$`, then `dependency-resolution` for `^(.*dependency.*resolution.*|.*resolve.*dependenc.*|resolve dependencies\b.*)$`, then `task-execution` for `^(.*task.*execution.*|.*execute.*tasks?.*|.*run.*tasks?.*|task execution\b.*)$`. Unmatched names are ignored. For every classified retained `PhaseState`, aggregation adds its `totalItems` and `completedItems` to that bucket, so repeated phases sum. `phaseCounts` always emits exactly these three buckets; any absent bucket is `{totalItems:0, completedItems:0}`, including `dependency-resolution` when no distinct dependency-resolution phase was observed.

`BuildProgressTracker` retains source data in `completedPhaseHistory`: it captures a `PhaseState` on phase finish before removing that state from the active stack. At completion, `RunningBuild` freezes that retained history into the `Build`, detached from live mutable progress state. Task provenance is aggregated into an `originPlugin -> count` map; tasks with absent provenance use the single reserved `_unknown` key, which is omitted when unused, and all aggregation values sum to the total completed task count.

This design answers post-build questions without creating another live monitoring protocol or exposing tracker internals.

### 4. Capture only the configuration-cache report path

The authoritative capture mechanism is an init-script marker analogous to `[MCP-BUILD-SCAN]`, for example `[MCP-CC-REPORT] <report-path>`, emitted with the configuration-cache report path. Console interception is fallback-only. The captured path is stored verbatim in `configCacheReportPointer: String?` and exposed with build output.

The absence of an emitted configuration-cache report is a legitimate correct-null result and is distinct from missing an emitted marker during capture. The server never opens or parses the report. Configuration-cache problems continue to flow through `ProblemAggregation` and remain queryable through `query_build kind=PROBLEMS`.

### 5. Keep environment and project-graph diagnosis in the skill

`src/main/skills/using-gradle/references/diagnostic-tasks.md` documents two routes using existing Gradle behavior. JDK and daemon questions use `javaToolchains`, `buildEnvironment`, and `--version` to distinguish IDE, CLI, daemon, and toolchain state. Project-ownership questions use `projects`, then `tasks --all`, then `help --task` to traverse multi-project, composite-build, and convention-plugin ownership.

No new MCP tools are justified because the existing Gradle tasks provide the authoritative data and the missing piece is agent routing.

## Risks / Trade-offs

- A single string reason is easy for agents to consume but less rigid than a structured reason type. Keeping outcome authoritative and reason explanatory limits ambiguity.
- Phase snapshots improve post-build explanation but do not provide live per-phase updates. This is intentional because live progress is outside this proposal.
- Origin aggregation depends on available provenance. The reserved `_unknown` bucket preserves unattributed tasks without inventing ownership.
- Configuration-cache pointer capture depends on the Gradle 9.7.0 init-script hook reliably surfacing the emitted report path. The Phase-0 research gate verifies that mechanism before implementation; console interception remains fallback-only, and the implementation must not infer a path.
- Additive output increases result size slightly, but avoids additional queries and file reads during diagnosis.

## Migration Plan

1. Add additive result fields with null or empty defaults so existing persisted and in-memory result construction remains valid during the change.
2. Populate all task outcome reasons, retain completed-phase history and freeze its normalized buckets, aggregate task origins including `_unknown`, and capture the configuration-cache report pointer at build completion.
3. Render the new fields through the relevant `query_build` views and update the `using-gradle` routing guidance.
4. Run `:updateToolsList` after tool metadata changes and include the resulting generated documentation updates.
5. Validate focused unit and integration coverage before running the repository's broader checks.

## Verification

- Unit-test task reason derivation and rendering for from-cache, up-to-date, no-source, general skipped, success, failure, and cancelled outcomes.
- Unit-test retained completed-phase history, precedence-ordered bucket classification, repeated-phase aggregation, unmatched-name exclusion, all three emitted buckets with 0/0 defaults, frozen phase snapshots, and task-origin aggregation, including `_unknown` provenance behavior.
- Integration-test `query_build` TASKS, DASHBOARD, and PROBLEMS output with the new fields and routing-relevant data.
- Integration-test authoritative configuration-cache report pointer capture and correct-null behavior on Gradle 9.7.0, and verify that the report is not opened or parsed.
- Test the `using-gradle` skill content for PROBLEMS-first, build-environment, and project-graph routing.
- Run `:updateToolsList` and verify generated tool and skill documentation is synchronized.
