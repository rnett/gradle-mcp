## 0. Verify configuration-cache report-path capture

- [ ] 0.1 Before implementing capture, research and verify on Gradle 9.7.0 that an init-script hook can reliably emit an authoritative `[MCP-CC-REPORT]` marker containing the configuration-cache report path across report-producing builds; document the verified hook and treat console interception as fallback-only.
- [ ] 0.2 Verify the gate distinguishes an emitted marker and captured path from a legitimate correct-null build where Gradle emits no configuration-cache report.

## 1. Complete the build-result code surface

- [ ] 1.1 Add nullable `reason` to `TaskResult` and preserve additive construction defaults.
- [ ] 1.2 Populate `TaskResult.reason` in `BuildExecutionService.handleTaskFinish` with the complete policy: `FROM_CACHE: isFromCache=true`, `UP_TO_DATE: isUpToDate=true`, preserved `NO_SOURCE` with `NO-SOURCE: <skipMessage verbatim>`, `SKIPPED: <skipMessage verbatim>` for every other `TaskSkippedResult`, and null for success, failure, and cancellation.
- [ ] 1.3 Render task outcome, reason, and provenance in `query_build kind=TASKS` output, printing `Reason:` whenever `reason` is non-null; add a focused `getTasksOutput` task for the preserved `NO_SOURCE` branch.
- [ ] 1.4 Capture each `PhaseState` in retained `completedPhaseHistory` when the phase finishes and before the active stack removes it, then freeze that history into the completed `Build` at completion per `RunningBuild`.
- [ ] 1.5 Implement the case-insensitive, trimmed phase-bucket classification function with top-down precedence `configuration` > `dependency-resolution` > `task-execution`; aggregate repeated retained states by summing totals and completions, ignore unmatched names, and always emit all three buckets with 0/0 for absent buckets.
- [ ] 1.6 Aggregate completed tasks by provenance origin plugin, group absent provenance under `_unknown`, verify all counts sum to the total completed task count, and expose `taskOriginAggregation` in DASHBOARD and console build output.
- [ ] 1.7 After the Phase-0 gate passes, capture the verbatim configuration-cache report path from the authoritative init-script marker, use console interception only as fallback, and expose nullable `configCacheReportPointer` without opening or parsing the report.
- [ ] 1.8 Preserve configuration-cache problems in `ProblemAggregation` and add the report-pointer hint to the relevant build and PROBLEMS output.

## 2. Update agent routing skills

- [ ] 2.1 Route FAILED and low-signal build diagnosis to `query_build kind=PROBLEMS` before file-read investigation.
- [ ] 2.2 Teach agents to interpret TASKS outcome reasons, frozen phase counts, task-origin aggregation, and the configuration-cache report pointer.
- [ ] 2.3 Add the build-environment route using `javaToolchains`, `buildEnvironment`, and `--version` for JDK, daemon, IDE, CLI, and toolchain questions.
- [ ] 2.4 Add the project-graph route using `projects`, `tasks --all`, and `help --task` for multi-project, composite-build, convention-plugin, and task-ownership questions.

## 3. Verify and synchronize generated documentation

- [ ] 3.1 Add focused unit tests for every task reason state, including preserved `NO_SOURCE`; retained `completedPhaseHistory`; precedence-ordered phase classification; repeated-phase summation; unmatched-name exclusion; absent-bucket 0/0 emission; frozen phase counts; `_unknown` task-origin aggregation with count conservation; and nullable configuration-cache pointers.
- [ ] 3.2 Add integration tests for TASKS, DASHBOARD, console, and PROBLEMS output, including configuration-cache runs on Gradle 9.7.0.
- [ ] 3.3 Add skill verification for PROBLEMS-first, build-environment, and project-graph routing.
- [ ] 3.4 Run `:updateToolsList` and include synchronized generated tool documentation and `docs/skills.md` updates.
- [ ] 3.5 Run the targeted tests, then the repository-required broader verification.
