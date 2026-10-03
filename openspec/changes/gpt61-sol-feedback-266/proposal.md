## Why

Issue #266 reports four independent agent-facing defects/frictions that degrade the feedback loop for build execution and dependency inspection: `captureTaskOutput` responses omit the build identity needed to correlate them (item 1); exact task/test path queries are rejected when a longer prefix also matches (item 2); `inspect_dependencies` runs a network-bound update check by default and renders "No projects found." when the dependency-report build is canceled (item 3); and completed-build phase counts render a misleading `3/0 completed` ratio when the total is unknown (item 4). Each item is independently judged; this change resolves all four together, with focused tests, regenerated tool documentation, and the full `check` gate.

## What Changes

- **Item 1 (captureTaskOutput build identity):** when `captureTaskOutput` is set and the build finishes, the response begins with `Gradle MCP Build ID: <id>`, a blank line, then the existing content (capture-failure warning if present, truncation hint + last 100 lines, or raw task output). The not-found fallback path is unchanged; no tool description change.
- **Item 2 (exact-path precedence):** in `getTasksOutput` and `getTestsOutput`, when `query` is non-empty, an exact match within the already-filtered candidate set takes precedence over longer prefix matches, so auto-expansion operates only on the exact-match subset. Listing paths, no-match diagnostics, `taskPath` prefix filtering, and exact-ID FAILURES/PROBLEMS behavior are unchanged. The `query` `@Description` and the tool-level auto-expansion sentence in the `query_build` description document this precedence.
- **Item 3 (dependency inspection):**
  - **3a — `checkUpdates` opt-in:** `InspectDependenciesArgs.checkUpdates` defaults to `false`; `updatesOnly=true` still forces it to `true`. Service-level and init-script behavior already read absence as false.
  - **3b — canceled build must fail loudly:** a dependency-report build that finishes with `BuildOutcome.Canceled` throws an explicit `IllegalStateException` on every `Canceled` outcome — stating the build was canceled and its output cannot be trusted, and suggesting a re-run — instead of falling through to structured parsing and printing "No projects found." or returning a partial report. The guard lives in the shared `getDependencies` funnel, so the `download*Sources` and source-set/configuration entry points inherit it.
  - No change to env/proxy handling (the issue's proxy-env hypothesis was rejected as root cause) or to the init script.
- **Item 4 (zero-total phase counts):** in `Build.toOutputString`, a bucket with `totalItems == 0` renders `"  <bucket>: <completedItems> completed"`, while `totalItems > 0` keeps the existing `"<completedItems>/<totalItems> completed"` ratio. Every bucket is still emitted; the data model is untouched. Ship-skill wording for "completed/total counts" is updated to match.
- Regenerate tool metadata (`:updateToolsList`) and update the affected ship documentation; run `:verifySkillsList` and the full `check`.

**BREAKING** (per AGENTS.md #8, agent-only consumers carry no backwards-compatibility obligation): the `checkUpdates` default flips from `true` to `false`; callers relying on implicit update checking must pass `checkUpdates=true`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-output-capturing`: "Task Output Capture Feedback" gains a captured-task-output scenario requiring the build-identity header line.
- `build-querying`: "Intelligent Auto-Expansion" gains exact-match precedence over longer prefix matches; "Completed build queries expose phase counts" gains the zero-total rendering rule.
- `update-check-output`: adds the opt-in `checkUpdates` default requirement and the explicit cancellation-failure requirement.

## Impact

- **Build execution tools:** `GradleExecutionTools` (`captureTaskOutput` response); `GradleBuildLookupTools` (auto-expansion in `getTasksOutput`/`getTestsOutput` and the `query` `@Description`); `GradleOutputs` (zero-total phase-count rendering).
- **Dependency tools:** `GradleDependencyTools` (`checkUpdates` `@Description` and default, tool-description bullet); `GradleDependencyService` (canceled-outcome guard adjacent to the `BuildOutcome.Failed` branch, placed once in the `getDependencies` funnel and inherited by every sibling entry point).
- **Tool metadata / generated docs:** the `inspect_dependencies` and build-query `query` description changes regenerate `docs/tools/PROJECT_DEPENDENCY_TOOLS.md` and `docs/tools/LOOKUP_TOOLS.md`; item 1 changes no description, so `:updateToolsList` is expected to produce no diff for it.
- **Ship documentation:** `src/main/skills/using-gradle/SKILL.md` and `references/diagnostic-tasks.md` phase-count wording; skill frontmatter is untouched.
- **Tests:** `GradleExecutionToolTest`, `GradleBuildLookupPrefixTest`, `GradleDependencyToolsTest`, a new dependency-service cancellation-guard test, and `BuildResultIntelligenceOutputTest`.
- **Compatibility:** no deprecation path; agent consumers only.
