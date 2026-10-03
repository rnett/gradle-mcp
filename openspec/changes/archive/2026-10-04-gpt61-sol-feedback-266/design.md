# Design

## Context

See `proposal.md` for issue #266 motivation. The four defects live in independent code paths and are each independently judged; the only coupling between them is the shared OpenSpec change and the final verification gates.

Current state relevant to each item:

- **Item 1:** `captureTaskOutput` responses are assembled in `GradleExecutionTools.kt:97-127` and do not carry the build identity. The canonical header wording lives in `GradleOutputs.kt:59`.
- **Item 2:** auto-expansion runs in `GradleBuildLookupTools.kt:246-299` (TASKS) and `:154-201` (TESTS) over the already-filtered candidate set; the `query` `@Description` is at `:49-50`.
- **Item 3:** `GradleDependencyService.getDependencies` throws only on `BuildOutcome.Failed` (`:408-413`); `BuildOutcome.Canceled` falls through to `parseStructuredOutput` (`:415-416`), and because `latestVersions.get()` runs before any PROJECT marker the report parses to zero projects, so `GradleDependencyTools.kt:113-118` prints "No projects found.". The tool's `checkUpdates` default and description sit at `GradleDependencyTools.kt:35-36`; the service-level `DependencyRequestOptions.checkUpdates` already defaults to false (`DependencyRequestOptions.kt:23`); `addProp` emits `-Pmcp.checkUpdates=true` only when true (`GradleDependencyService.kt:378-384`); the init script reads absence as false (`dependencies-report.init.gradle.kts:1088`).
- **Item 4:** per-bucket phase rendering is `GradleOutputs.kt:163-165`.
- **Rejected hypothesis:** the issue's proxy-env hypothesis for the item-3 stall is rejected as root cause. Environment is inherited by default (`GradleArgs.kt:61`) and applied at `BuildExecutionService.kt:164-166`.

## Goals / Non-Goals

**Goals:**

- Eliminate all four agent-facing defects/frictions with the smallest behavior change per item.
- Preserve every neighboring behavior: listing/no-match diagnostics, `taskPath` prefix filtering, exact-ID FAILURES/PROBLEMS handling, not-found capture fallback, the `dep = {completed}/{total}` ratio when a total is known, and all env/proxy handling.
- Cover each item with focused deterministic tests, keep generated tool docs synchronized, and pass `check` plus `verifySkillsList`.

**Non-Goals:**

- Changing environment/proxy inheritance or the dependency-report init script (the stall hypothesis is rejected; see Context).
- Reordering init-script markers.
- Adding a per-candidate update-check progress or timeout knob (optional follow-up).
- Changing `completed > total` rendering when `total > 0`.
- Changing TESTS `taskPath` from a prefix filter.
- Changing gradle-tool canceled-status rendering.
- Adding backwards-compatibility shims (AGENTS.md #8: consumers are agents).

## Decisions

### 1. Item 1 — prefix captured task output with the build identity

When `captureTaskOutput` is set and the build finishes, the response SHALL begin with `Gradle MCP Build ID: <id>` followed by a blank line, then the existing content: the capture-failure warning if present, otherwise the truncation hint plus the last 100 lines, or the raw task output. The header wording is byte-identical to `GradleOutputs.kt:59`. The not-found fallback path (which already appends the full `toOutputString()`) is unchanged. No tool description changes, so `:updateToolsList` is expected to produce no diff for this item.

**Alternative rejected:** emitting the build ID as a trailing hint or inside the truncation hint only. A leading, uniform header is what lets an agent correlate a captured response with the build that produced it regardless of which content branch renders.

### 2. Item 2 — exact match takes precedence over longer prefix matches

In both `getTasksOutput` and `getTestsOutput`, when `query` is non-empty, compute the prefix-matched set exactly as today (including outcome/taskPath filters). If any member of that filtered set is an exact match — `path == query` for TASKS, `fullName == query` for TESTS — auto-expansion operates only on the exact-match subset; otherwise current prefix behavior applies. For TASKS the exact subset is a single task, so the detail view renders with no "Note:" line. For TESTS the exact subset may contain multiple executions of the same test, so the existing `testIndex` selection is unchanged. Listing paths (multi-match list, no-match diagnostics, `taskPath` prefix filtering) are unchanged; exact priority applies only within the already-filtered set. `taskPath` stays a prefix filter, and FAILURES/PROBLEMS stay exact-ID.

The `query` `@Description` (`:49-50`) is extended with "(an exact match takes precedence over longer prefix matches)", and the tool-level auto-expansion sentence at `:536` is replaced, because "matches exactly one item" becomes inaccurate once an exact path expands despite longer-prefix siblings. The replacement sentence is:

> If a query for TASKS, TESTS, FAILURES, or PROBLEMS matches exactly one item, it auto-expands to full details; for TASKS and TESTS, an exact path or test-name match auto-expands even when longer prefix matches exist. Otherwise, it returns a summary list with a hint to refine the query.

Both changes regenerate `docs/tools/LOOKUP_TOOLS.md`. The pre-existing looseness for FAILURES/PROBLEMS with a wrong ID (a not-found message, not a summary list) predates this change and is left as is.

**Alternative rejected:** changing the matching function to shell out an exact lookup before the prefix search. Filtering first and then preferring the exact member preserves outcome/taskPath filtering, keeps one candidate pipeline, and avoids a second query path that could diverge.

### 3. Item 3a — make `checkUpdates` opt-in

`InspectDependenciesArgs.checkUpdates` defaults to `false`. `updatesOnly=true` still forces it to `true` (unchanged; see the `update-check-output` Notes "updatesOnly implies checkUpdates"). The service layer already honors this: `DependencyRequestOptions.checkUpdates` defaults false, `addProp` emits `-Pmcp.checkUpdates=true` only when true, and the init script reads absence as false. `onlyDirect=true` remains the default. The `@Description` (≤100 chars) becomes "Opt-in authoritative newer-version check (network-bound). Forced `true` by `updatesOnly=true`." and the tool-description bullet at `:58` is replaced with the wording below; both changes regenerate `docs/tools/PROJECT_DEPENDENCY_TOOLS.md`:

> - **Update Check**: opt-in via `checkUpdates=true` — authoritatively checks project repositories for newer versions (network-bound); individual lines show `[UPDATE AVAILABLE: X.Y.Z]`; use `updatesOnly=true` for a flat summary: `group:artifact: current → latest` with the project paths where each dep is used (forces `checkUpdates=true`). Use `stableOnly=true` to exclude pre-release versions.

**Alternative rejected:** keeping `checkUpdates` default-on with a "skip when a proxy is detected" heuristic. The proxy hypothesis is rejected (Context), and opt-in removes the network-bound default entirely.

### 4. Item 3b — a canceled dependency-report build fails explicitly

After `awaitFinished()` (`:407`), any `BuildOutcome.Canceled` outcome throws an `IllegalStateException` — unconditionally, regardless of whether partial structured output was emitted. A canceled report is never a trustworthy report: partial emission is silent truncation, and returning a fragment that masquerades as the graph is the same class of defect as the empty "No projects found." report. The guard sits adjacent to the existing `BuildOutcome.Failed` branch (`:408-413`), mirrors it by logging the raw console output at info before throwing, and its message states that the dependency-report build was canceled and its output cannot be trusted, and suggests re-running (mentioning the `checkUpdates` opt-in when relevant).

**Blast radius:** the guard is placed once in `getDependencies`, the single dependency-report resolution funnel. Every sibling entry point resolves through it and inherits the guard: `getSourceSetDependencies` (`:467-511`), `getConfigurationDependencies` (`:513-563`), and the four `download*Sources` entry points (`:565-642`). Each of those paths would otherwise surface a cancel as a misleading different error (an empty report, or "Project not found in report"); with the guard they all fail with the same explicit cancellation error. No separate guards are added, and no sibling is delimited as unchanged — their observable behavior changes by design.

**Alternative rejected (condition):** throwing only when no structured data was emitted. Partial emission is precisely the case where a silently truncated report is most misleading, and the condition would add a pre-parse console scan plus its own tests for a strictly worse contract.

**Alternative rejected (exception type):** throwing `CancellationException`. Build cancellation and tool-coroutine cancellation are distinct; a client cancel already suppresses the response via the SDK (`mcp-server-composition:14-37`), so the outcome-driven failure is a normal tool error, not coroutine cancellation.

### 5. Item 4 — zero-total buckets render a bare count

In `Build.toOutputString` (`GradleOutputs.kt:163-165`), per bucket: `totalItems == 0` renders `"  <bucket>: <completedItems> completed"`; `totalItems > 0` keeps the existing `"${completedItems}/${totalItems} completed"`. Absent buckets (0/0) render `<bucket>: 0 completed`, and every bucket is still emitted. A raw `completed > total` count with `total > 0` continues to render the ratio. The data model is untouched. Ship-skill wording at `src/main/skills/using-gradle/SKILL.md:52` and `references/diagnostic-tasks.md:51-53` changes from "completed/total counts" to "a count when the total is unknown (0), otherwise completed/total"; skill-body only, frontmatter untouched. No tool metadata change.

**Alternative rejected:** treating a zero total as "hide the phase count" or as an overflow indicator. A bare count is the least surprising rendering and preserves the invariant that every bucket is emitted.

### 6. Cross-item sequencing

No code paths are shared between items. Implementation proceeds Phase A item 4, Phase B item 1, Phase C item 2, Phase D item 3, then Phase E gates: `./gradlew :updateToolsList` followed by `./gradlew check verifySkillsList`. The only cross-item coupling is the final gates and this OpenSpec artifact.

## Risks / Trade-offs

- **Risk:** flipping `checkUpdates` to default-off silently drops update results for callers that relied on the old default. → **Mitigation:** the behavior is documented in the tool description and spec, and `updatesOnly=true` remains a forcing path; agent consumers carry no compatibility obligation (AGENTS.md #8).
- **Risk:** exact-match precedence could mask a genuinely ambiguous query. → **Mitigation:** precedence applies only when an exact match exists in the filtered set; the ambiguous-prefix listing path is preserved and covered by a test.
- **Risk:** a canceled build guard could change behavior for callers that previously received a (misleading) empty report. → **Mitigation:** the empty report was never actionable; an explicit cancellation error is strictly more informative and is covered by a focused service-guard test.
- **Risk:** zero-total rendering could confuse readers expecting symmetry. → **Mitigation:** the rule is stated in the spec and asserted by tests, and the ratio path is untouched for known totals.

## Migration Plan

1. Phase A — item 4 rendering in `GradleOutputs` plus skill-body wording; add `BuildResultIntelligenceOutputTest` cases.
2. Phase B — item 1 build-identity header in `GradleExecutionTools`; add `GradleExecutionToolTest` cases (successful capture, truncation, not-found regression).
3. Phase C — item 2 exact-match precedence in `GradleBuildLookupTools` plus the `query` description; extend `GradleBuildLookupPrefixTest`.
4. Phase D — item 3 `checkUpdates` opt-in default/description and the canceled-outcome guard; extend `GradleDependencyToolsTest` and add the service-guard test.
5. Phase E — run `./gradlew :updateToolsList`, then `./gradlew check verifySkillsList`; inspect the regenerated `docs/tools/LOOKUP_TOOLS.md` and `docs/tools/PROJECT_DEPENDENCY_TOOLS.md` and confirm no unexpected diffs (item 1 expected none).

Rollback is per item: revert the single file change and its tests; the items are independent. The change is archived only after all gates pass.

## Open Questions

- **Resolved during review:** the canceled-build requirement stays in `update-check-output` as an ADDED requirement (the dependency-report tool family is that capability's subject), now worded at the `getDependencies`-funnel level with the unconditional condition. No standalone capability is introduced.
- **Residual — `PhaseCount` positional order:** confirm at its definition during implementation that `PhaseCount(totalItems=0, completedItems=3)` is constructed in the intended order (task 1.2's test assumptions).
- **Residual — issue author's post-cancel response:** the issue author reportedly saw a response after cancelling; this is most likely a client abort/timeout rather than an MCP request-cancel. It does not affect the chosen guard and no root-cause claim is made.
- **Residual — underlying network cause of the stall:** moot after making `checkUpdates` opt-in; no root-cause claim is made.

## Verification

Planned verification (results recorded during apply):

- **Item 1:** `GradleExecutionToolTest` — successful capture ≤100 lines contains `Gradle MCP Build ID: <id>` plus task output; truncation >100 lines has the header and the existing inline-buildId truncation hint; not-found fallback regression.
- **Item 2:** `GradleBuildLookupPrefixTest` — exact-over-longer-prefix TASKS; exact-over-longer-prefix TESTS; exact name with multiple executions plus `testIndex`; ambiguous prefix still lists. Existing `:249-279` cases stay green.
- **Item 3:** `GradleDependencyToolsTest` (MockK seam) — default args yield `checkUpdates == false`; `updatesOnly=true` yields true; explicit true yields true; default response has no `[UPDATE CHECK SKIPPED]`. New service-guard unit test with a mocked `GradleProvider`/`RunningBuild` using the `FinishedBuild(outcome = BuildOutcome.Canceled)` synthetic-build pattern from `BuildResultIntelligenceOutputTest:24-46`, asserting an `IllegalStateException` containing "cancel" for both an empty console and one already containing structured PROJECT markers (the guard is unconditional), plus one sibling entry point (e.g. `downloadAllSources`) to lock the funnel routing; note `getDependencies` runs under `context(progress: ProgressReporter)`. Existing integration tests construct `DependencyRequestOptions` directly and are unaffected.
- **Item 4:** `BuildResultIntelligenceOutputTest` — configuration `PhaseCount(totalItems=0, completedItems=3)` yields `configuration: 3 completed` and not `3/0`; a zero/zero bucket yields `dependency-resolution: 0 completed`; existing ratio assertions unchanged.
- **Gates:** `./gradlew :updateToolsList` then `./gradlew check verifySkillsList`.
