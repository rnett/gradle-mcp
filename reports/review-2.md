# 🏛️ Faceted Review Report — `inspect_build` Refactor (2026-04-27)

## Executive Summary

This changeset successfully splits the monolithic `inspect_build` tool into `query_build` (pure querying) and `wait_build` (pure waiting), simplifies parameter surfaces, and unifies `TaskOutcome`/`TestOutcome` into `BuildComponentOutcome`.
The architectural direction is sound, but the implementation has a cluster of critical issues: the `waitForFinished` parameter is fully dead (never read), the auto-generated `LOOKUP_TOOLS.md` schema is stale and actively wrong (exposing
non-existent field names and enum values to all LLM agents), a concurrency race in `completingTasks` can cause `waitForTask` to fail in parallel builds, and two tool code paths (`kind=FAILURES` and the timeout expiry) have zero test
coverage. These must be addressed before the change is reliable in production.

---

## 🔴 Critical (Must Address)

**1. [Logic / Arch / Spec]** `[Mistake]`: `WaitBuildArgs.waitForFinished` is never read — the parameter is completely dead.

`finishJob = async { runningBuild.awaitFinished() }` is created unconditionally and always registered in the `select {}` block regardless of `waitForFinished`'s value. Calling `wait_build(waitForFinished=false)` blocks identically to
`wait_build(waitForFinished=true)`. The tool description explicitly tells callers that `waitForFinished=true` is a supported mode, making this a broken API surface.

- **Recommendation**: Gate `finishJob` entry into the `select` on `waitForFinished || (waitForRegex == null && waitForTask == null)`. Or remove the parameter entirely and document the implicit-finish-default.

**2. [Logic / Arch]** `[Mistake]`: `getTestsOutput` — `testIndex` bounds guard has an inverted condition causing `IndexOutOfBoundsException` on single-match results.

```kotlin
// BUG: guard only fires when matched.size > 1
if (matched.size > 1 && targetIndex >= matched.size) { return "..." }
val test = matched[targetIndex]  // crashes when size==1 and testIndex==1
```

When exactly one test matches a query and the caller passes `testIndex=1`, the guard is skipped (`size > 1` is false) and `matched[1]` throws uncaught.

- **Recommendation**: Remove `matched.size > 1 &&`. The condition should simply be `if (targetIndex >= matched.size)`.

**3. [Risk / Doc]** `[Mistake]`: `LOOKUP_TOOLS.md` schema is stale and exposes wrong field names and enum values to all LLM agents. Specifically:

- (a) Schema shows two fields `taskOutcome` and `testOutcome`; the actual `QueryBuildArgs` has a single `outcome: BuildComponentOutcome?` field.
- (b) Schema lists `PASSED` as a valid enum value; `BuildComponentOutcome` has no `PASSED` member — it is `SUCCESS`. Any agent filtering for passed tests with `outcome="PASSED"` gets a silent zero result.
- (c) Schema lists `timeout` in `required[]` for `wait_build`; the Kotlin default is `600.0`, making it optional. The prose says "Must be provided." which is outright wrong.

This appears to be a stale auto-generated file — `./gradlew :updateToolsList` was likely not run, or the generator does not reflect the new unified field.

- **Recommendation**: Run `:updateToolsList`. Manually verify the generated schema matches `QueryBuildArgs` and `WaitBuildArgs`. Until fixed, all agents using either tool are operating against an incorrect contract.

**4. [Concurrency]** `[Mistake]`: `completingTasks` SharedFlow has `replay=1`, creating a reproducible race when two tasks complete in rapid succession.

The `taskJob` pattern is: (1) check `completedTaskPaths` snapshot, (2) fall through to `completingTasks.firstOrNull { it == waitForTask }`. With `replay=1`, if the target task completes and then a second task immediately completes, the
target's emission is evicted from the replay buffer before `taskJob` subscribes. The `firstOrNull` will never see the target path, blocking indefinitely until `finishJob` wins and throws `"Build finished without completing task: ..."` — a
spurious failure in a valid parallel build.

- **Recommendation**: Increase `replay` on `_completingTasks` to match expected parallel task concurrency (e.g., 50), or re-check `completedTaskPaths` after `firstOrNull` returns null before returning false.
- **File**: `RunningBuild.kt`, `_completingTasks` flow declaration.

**5. [Test]** `[Mistake]`: The `wait_build` timeout-expiry path is completely untested.

The production path returning `"Wait timed out after ${args.timeout}s. Current console tail:\n\n..."` has no test. The test named `wait_build waits for completion` does not trigger a timeout — the build is stored as finished before the tool
is invoked.

- **Recommendation**: Add a test where `withTimeoutOrNull` actually expires: use a `MutableSharedFlow` that never emits for `awaitFinished()` and let `testScheduler.advanceTimeBy(timeout)`.

**6. [Test]** `[Mistake]`: `kind=FAILURES` has zero test coverage across all test files.

`getFailuresOutput` (line 320 of `GradleBuildLookupTools.kt`) handles the primary path for diagnosing broken builds. `syntheticBuildResult()` in `McpToolWorkflowsTest` constructs a build with a real `Failure(FailureId("f1"), ...)`, but no
test ever calls `query_build(kind=FAILURES)`. The exact-ID auto-expand path and the list fallback path are both untested.

- **Recommendation**: Add unit tests in `GradleBuildLookupPrefixTest` covering at minimum: (a) exact failure ID lookup → auto-expands to details, (b) no query → returns list of all failures.

**7. [Logic]** `[Mistake]`: `getConsoleOutput` — `hasMore` off-by-one: uses `pos <= output.length` (inclusive) causing a spurious "more results" hint when the output is exactly `limit` lines long.

When the buffer is exactly `offset + limit` lines, the loop exhausts precisely at `pos == output.length`. The condition `pos <= output.length` is true (no remaining content) but `hasMore` is set to `true`, suggesting pagination when none is
needed.

- **Recommendation**: Change `pos <= output.length` to `pos < output.length`.

---

## 🟡 Major (Highly Recommended)

**8. [Logic]** `[Mistake]`: `afterCall=true` contract is silently violated for already-finished builds.

When `wait_build` is called on a `FinishedBuild`, the `else` branch calls `waitForMatch(build, args)`. `waitForMatch` ignores `args.afterCall` entirely — it always scans the entire console log. A caller using `afterCall=true` on a finished
build gets a match against pre-existing output, breaking the "only match events emitted after this call" semantic.

- **Recommendation**: In the already-finished branch, if `afterCall=true`, the function should return no-match (since no events can be emitted after the call for a finished build), or throw a clear error.

**9. [Logic / Arch / Doc / Spec]** `[Intentional — Flawed]`: `PASSED` → `SUCCESS` rename creates a three-way inconsistency between the implementation, the design doc, and the specs.

The design doc (`design.md`, line 31) explicitly states the mapping is `SUCCESS/PASSED → PASSED`. The implementation chose `SUCCESS`. The specs (`build-querying/spec.md`, `archived/specs/build-querying/spec.md`) show `outcome="PASSED"` in
scenarios — a value that does not exist in the enum. Additionally, `SUCCESS` now means both "task executed successfully" and "test assertions passed" within the same enum, which is a semantic collision that may confuse agents filtering by
outcome.

- **Recommendation**: Pick one canonical name and apply it everywhere. Either rename `SUCCESS` → `PASSED` in the enum (and update 3 sites in `BuildExecutionService`) or update all specs/docs to use `SUCCESS`. Do not leave specs and code
  diverged.

**10. [Logic / Arch]** `[Mistake]`: `outcome.javaClass.simpleName` is fragile and produces inconsistent casing.

In `wait_build` (line 685): `"Build finished with outcome: ${(build as FinishedBuild).outcome.javaClass.simpleName}"`. This yields `"Success"` (capitalized) while the dashboard renders `"SUCCESS"` (all caps). Using `.javaClass.simpleName`
is fragile under obfuscation and Kotlin sealed class naming conventions.

- **Recommendation**: Replace with an explicit `when` expression: `when (outcome) { is BuildOutcome.Success -> "SUCCESS"; is BuildOutcome.Failed -> "FAILED (${outcome.failures.size} failures)"; is BuildOutcome.Canceled -> "CANCELED" }`.

**11. [Concurrency / Clean Code]** `[Mistake]`: `build = buildResults.getBuild(args.buildId)!!` after the wait is an unguarded null dereference.

If the build is evicted from the `BuildManager` cache between `awaitFinished()` completing and this re-fetch, the `!!` throws a `NullPointerException` with no diagnostic message.

- **Recommendation**: Replace with `?: throw IllegalArgumentException("Build ${args.buildId} expired from cache after wait completed")`.
- **File**: `GradleBuildLookupTools.kt`, line 669.

**12. [Test]** `[Mistake]`: `wait_build` already-finished + condition-not-met path is untested.

The code at line 674–677 throws `"Build already finished without $condition"` when a finished build does not satisfy the requested `waitFor`/`waitForTask`. This path has no test.

- **Recommendation**: Add a test with a `FinishedBuild` where the required regex/task is absent from the build state.

**13. [Test]** `[Intentional — Flawed]`: `McpToolWorkflowsTest.inspect tools can read stored build` has no content assertions — only existence checks.

Five `query_build` variants are called but all assertions are `assert(result != null)`. This proves the tool doesn't throw, but would not catch any regression in response content.

- **Recommendation**: Add at minimum one textual assertion per query kind (e.g., check that `kind=TESTS` response contains `BuildComponentOutcome.SUCCESS` test name, `kind=DASHBOARD` contains the build ID).

**14. [Test]** `[Mistake]`: `McpToolWorkflowsTest` constructs tool arguments using raw `JsonObject(mapOf(...))`, violating the project mandate to use `buildJsonObject`.

`AGENTS.md` mandates: "When calling MCP tools from tests using `server.client.callTool`, always use `kotlinx.serialization.json.buildJsonObject`." Raw maps risk silent serialization mismatches if the schema evolves.

- **Recommendation**: Migrate all `server.client.callTool` argument construction in `McpToolWorkflowsTest` to `buildJsonObject { }`.

**15. [Test]** `[Mistake]`: `wait_build waits for completion` test is misnamed and exercises the already-finished path, not the "waits for" path.

The mock stores the build as finished before the tool call. The test is structurally identical to `wait_build returns immediately if build already finished and no condition`.

- **Recommendation**: Rename the test to reflect what it actually tests, or redesign it to use virtual time and a build that finishes asynchronously mid-call.

**16. [Test]** `[Mistake]`: `wait_build errors if build finishes without waitFor match` uses a real 1-second wall-clock `delay` instead of virtual time.

`server.scope.launch { delay(1000) ... }` does not use the `testScheduler`. On a slow machine or cold-cache run, the tool may return before `storeResult` is called, exercising a different code path than intended.

- **Recommendation**: Use `testScheduler.advanceTimeBy(...)` or restructure the test to use `launch(UnconfinedTestDispatcher) { ... }` coordinated with the test scheduler.

**17. [Test]** `[Mistake]`: `GradleBuildLookupOutputFileTest` uses `requireNotNull(text)` at multiple sites, violating the Power Assert mandate.

`AGENTS.md` states to avoid `!!`-equivalent patterns before assertions. `requireNotNull` strips Power Assert of the ability to display the actual null value in failure output.

- **Recommendation**: Replace `requireNotNull(text); assertTrue(text.contains(...))` with `assertTrue(text?.contains(...) == true)` or `assertNotNull(text); assertTrue(text!!.contains(...))` only after a typed assertion.

**18. [Logic]** `[Mistake]`: `IN_PROGRESS` tests are silently omitted from the test count breakdown in `getTasksOutput`.

The task output reports `"Tests: N (X passed, Y failed, Z skipped)"` where `N` is `relatedTests.size`, but `passedCount + failedCount + skippedCount + cancelledCount` may be less than `N` because `IN_PROGRESS` tests are counted in the total
but not in any subcount. This produces visibly incorrect arithmetic during running builds.

- **Recommendation**: Either pre-filter `relatedTests` to exclude `IN_PROGRESS`, or add an `inProgressCount` line to the output.

**19. [Clean Code]** `[Mistake]`: The `--- BUILD IN PROGRESS ---` / `--- BUILD FINISHED ---` header is duplicated between `getDashboardOutput` and the `queryBuild` lambda.

Both generate the same header text via independent `if (build is RunningBuild)` checks. A wording change must be made in two places.

- **Recommendation**: Extract `fun buildStatusHeader(build: Build): String` and call it from both sites.

**20. [Clean Code]** `[Unclear Intent]`: `waitForMatch` accepts `Build` but its `RunningBuild` branch is unreachable at its only call site.

The sole call is inside `else { // build !is RunningBuild }`, so the `if (build is RunningBuild && ...)` branch inside `waitForMatch` can never execute.

- **Recommendation**: Either tighten the signature to `FinishedBuild` and remove the dead branch, or add a comment explaining the general signature is intentional for future use.

**21. [Doc]** `[Mistake]`: `running_gradle_builds/SKILL.md` (and `running_gradle_tests/SKILL.md`) describes wrong semantics for `wait_build`'s blocking behavior.

The skill says "If `timeout` is set without a wait condition, the tool waits for the build to finish." The correct framing is: "If neither `waitFor` nor `waitForTask` is specified, `wait_build` defaults to waiting for the build to finish."
Framing it as requiring `timeout` to be set is misleading — the default timeout is 600s.

**22. [Doc]** `[Mistake]`: `running_gradle_tests/SKILL.md` example for "List all failed tests" is missing `"kind": "TESTS"`.

Without `kind`, the call defaults to `kind=DASHBOARD` and the `outcome` filter is silently ignored. Agents copy-pasting this example will receive a build summary, not a test list.

- **Recommendation**: Add `"kind": "TESTS"` to the example JSON.

**23. [Doc / Spec]** `[Mistake]`: `design.md` states the unified enum maps `SUCCESS/PASSED → PASSED` but the implementation uses `SUCCESS`. Multiple specs show `outcome="PASSED"` in scenarios, which is an invalid enum value.

This is the root cause of finding 3 (stale schema). The design doc should be updated to reflect the implemented decision, or the code should be updated to match the design doc.

**24. [Spec]** `[Mistake]`: `.openspec.yaml` for `2026-04-26-redesign-inspect-build` is missing the required `status` field.

The file only contains `schema` and `created` fields. The parallel archived change has a `status` field. Without it, automated tooling cannot distinguish this archived change from an in-progress one.

- **Recommendation**: Add `status: archived` (or `completed`) to the YAML.

**25. [Spec]** `[Intentional — Flawed]`: The `console-regex-filtering` spec requires line numbers in matched output but the non-regex (tail/head) path emits none, leaving the console output format inconsistent.

The spec mandates "prefixed with their original line number" for the regex path. The tail/head path emits raw lines. An agent that notes line numbers from a filtered call cannot use them as anchors in an unfiltered call.

- **Recommendation**: Explicitly state in the spec whether non-regex console output is line-numbered, and implement consistently.

---

## 🔵 Minor (Polishing / Idioms)

**26. [Logic / Multiple]** `[Mistake]`: `WaitBuildArgs.waitForFinished` description contradicts its default value. Description says "Default if no other wait condition is provided" implying it defaults to `true`-like behavior; the declared
default is `false`. This is doubly confusing given finding 1.

**27. [Concurrency]** `[Unclear Intent]`: `progressJob` is dispatched to `Dispatchers.Default`. If `progressReporter.report()` does I/O, this is the wrong dispatcher per the project's dispatcher-injection mandate. If it is a lightweight
in-memory update, the explicit dispatcher is unnecessary overhead.

**28. [Concurrency]** `[Intentional — Flawed]`: A secondary exception from `progressJob` running concurrently with the `finishJob` RuntimeException will suppress the meaningful diagnostic via `coroutineScope` exception aggregation, making
the error undiagnosable.

**29. [Concurrency]** `[Intentional — Flawed]`: `completedTaskPaths: Set<String>` creates a full defensive `toSet()` copy on every access. The underlying `ConcurrentHashMap.newKeySet()` supports safe concurrent iteration and `.contains()`
without copying. This is a gratuitous allocation in a potentially hot path.

**30. [Concurrency]** `[Intentional — Flawed]`: The `finally` block calls `cancelAndJoin()` on `progressJob` but only `cancel()` on `regexJob`, `taskJob`, and `finishJob`. The asymmetry is misleading — `coroutineScope` waits for all
children regardless, making the explicit `cancelAndJoin` partially redundant. If structured concurrency guarantees are ever weakened, the other three jobs become leaks.

**31. [Clean Code]** `[Mistake]`: The `QueryKind.DASHBOARD -> ""` arm inside the `when` header expression (inside the `else { // kind != DASHBOARD }` branch) is dead code that can never execute. It should be
`QueryKind.DASHBOARD -> error("unreachable")` to make the invariant explicit and catch future refactoring bugs.

**32. [Clean Code]** `[Mistake]`: Inconsistent return style in `waitBuild` — `return@tool` for the timeout early exit, expression body for the normal return. Prefer one consistent style throughout a lambda.

**33. [Clean Code]** `[Mistake]`: `occurences` is misspelled throughout the codebase (appears in `Problems.kt`, `ProblemsAccumulator.kt`, `GradleOutputs.kt`, `GradleBuildLookupTools.kt`). Correct spelling is `occurrences`. Because it
appears in serialized `data class` fields, fixing it is a schema-breaking change that should be coordinated with a version bump.

**34. [Clean Code]** `[Mistake]`: `getConsoleTail` duplicates the reverse-scan-from-end algorithm already present in the `offset==0` branch of `getConsoleOutput`. If the edge-case handling changes (e.g., for `\r\n`), it must be updated in
two places.

- **Recommendation**: `getConsoleTail` should call the shared tail-scan logic rather than re-implementing it.

**35. [Clean Code]** `[Mistake]`: Line 224 of `GradleBuildLookupTools.kt` is ~200+ characters — an inline string template with an embedded `mapValues` lambda. Extract the metadata truncation to a local variable or named function.

**36. [Clean Code]** `[Mistake]`: `Models.kt` is missing a trailing newline after the final `}`.

**37. [Clean Code]** `[Mistake]`: `actualLimit` guard in `getConsoleOutput` (`if (limit == Int.MAX_VALUE && args.outputFile == null) 20 else limit`) is an unreachable dead-code path. When `outputFile == null`, the caller never sets
`limit = Int.MAX_VALUE`. The dead condition confuses readers about when the 20-line cap applies.

**38. [Logic]** `[Minor inefficiency]`: `getTestsOutput` calls `build.testResults.all` twice in the "not found" fallback path (lines 204, 211), triggering two full recomputation passes over all tests. The results were already materialized
in `matched` earlier.

**39. [Doc]** `[Mistake]`: `background_monitoring.md` never mentions the `afterCall: Boolean` parameter, which is critical for polling patterns (repeated `wait_build` calls on the same build without re-triggering on previously seen log
lines).

**40. [Spec]** `[Intentional — Flawed]`: `openspec/specs/build-monitoring-progress/spec.md` was not promoted from delta format. Unlike `build-monitoring/spec.md` and `build-querying/spec.md` which have `## Purpose` / `## Requirements`
structure, this file retains the `## ADDED Requirements` change-diff format.

**41. [Risk]** `[Mistake]`: `McpToolWorkflowsTest` retains stale `// inspect_gradle_build (dashboard)` comments while calling `ToolNames.QUERY_BUILD`. These comments reference the removed tool name and mislead future maintainers.

**42. [Test]** `[Intentional — Flawed]`: `FinishedBuildTest` uses bare `assert(condition)` throughout. Replacing `assert(lines.size == 3)` with `assertEquals(3, lines.size)` produces better Power Assert failure messages.

**43. [Test]** `[Mistake]`: `ViewMergingIntegrationTest` uses `runTest(timeout = 5.minutes)`. The project mandate is 10 minutes for integration tests that trigger Gradle builds or start external processes.

**44. [Test]** `[Mistake]`: `test outputFile returns error message on failure` in `GradleBuildLookupOutputFileTest` uses the Unix path `/invalid/path/that/should/fail/`. On Windows this may resolve to a real drive root and fail for
different reasons than intended. Use a guaranteed-impossible path via temp dir + non-existent subdirectory.

**45. [Arch]** `[Intentional — Flawed]`: `WaitBuildArgs.timeout` uses `Double` (seconds) rather than `kotlin.time.Duration`, which is used consistently elsewhere in the codebase. This is a minor stylistic regression.

**46. [Arch]** `[Mistake]`: `args.waitFor?.toRegex()` and `query.toRegex()` in `wait_build` / `getConsoleOutput` throw raw `PatternSyntaxException` on invalid input. An agent passing `"["` as a regex receives a raw stack trace rather than a
clear error message.

- **Recommendation**: Wrap in `try/catch` and return a clear `"Invalid regex pattern: ..."` message.

**47. [Arch]** `[Intentional — Flawed]`: `GradleBuildLookupTools.kt` is ~692 lines and growing. Six rendering concerns (dashboard, console, tasks, tests, failures, problems) each have private/internal methods of 30–100 lines, plus two
80–100 line tool registration lambdas. The `internal` visibility on rendering methods exists solely for test access. Consider extracting a `BuildResultRenderer` or similar with `@VisibleForTesting` methods as the file approaches 800 lines.

---

## ❓ Open Questions for the Author

- **[Arch/Logic]** `[Unclear Intent]`: Was `waitForFinished` meant to be an explicit opt-in (so calling `wait_build` with only a regex and `waitForFinished=false` would NOT block on build-finish if the regex fires first), or was the intent
  for `finishJob` to always race regardless, with `waitForFinished` being purely documentary? The answer determines whether finding 1 is a "remove the dead parameter" fix or a "gate `finishJob` on the flag" fix.

- **[Arch]** `[Unclear Intent]`: The `DASHBOARD` kind with a `buildId` returns navigational prose ("How to Inspect Details") embedded in a data retrieval tool. Is this intentional for ergonomic "first call is always instructional" behavior,
  or a pragmatic shortcut? A dedicated resource endpoint would separate tutorial content from data retrieval.

- **[Concurrency]** `[Unclear Intent]`: Was `replay=1` on `completingTasks` a deliberate memory trade-off, or set without considering the check-then-subscribe race in `taskJob`? The fix (raise replay to ~50) is simple if the answer is "
  oversight."

- **[Spec]** `[Unclear Intent]`: Were `taskOutcome` / `testOutcome` (as seen in the stale schema) an intended split of the `outcome` field — representing a planned but unimplemented API change — or is the schema simply stale? If the split
  is intentional, the Kotlin `QueryBuildArgs` must be updated.

---

## ✅ Positive Observations

- The tool split (`query_build` + `wait_build`) correctly separates the concerns of asking "what happened?" from "when will X happen?" — this makes tool call sequences more predictable for agents and easier to test.
- The `QueryKind` enum and `when`-dispatch in `queryBuild` is clean and exhaustive; each kind delegates to a focused private method, which is the right structure.
- The `wait_build` coroutine structure correctly uses `coroutineScope` + `select` for race-to-first semantics, and the exception propagation through `select` → `coroutineScope` → `withTimeoutOrNull` is correct for the `RuntimeException`
  case (it is not swallowed by `withTimeoutOrNull`).
- The `afterCall` pattern (checking existing state before subscribing to the flow) correctly handles the "already satisfied" case for both `regexJob` and `taskJob`.
- Test coverage for `getTestsOutput` auto-expand (`unique prefix match → detail view`) is solid and directly tests the described behavior.
- `BackgroundBuildStatusWaitTest` is well-structured overall, uses virtual time correctly for most tests, and covers the `afterCall` semantics for both `waitFor` and `waitForTask`.

---

## 📋 Review Status

- **Pass**: 1
- **Open questions remaining**: 4
- **Total findings**: 47 (7 Critical, 18 Major, 22 Minor)
- **Next step**: Author resolves open questions; findings 1–3 (dead parameter, bounds guard, stale schema) and finding 4 (replay race) are the highest-leverage fixes before any re-pass.
