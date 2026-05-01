ðŸ›ï¸ Faceted Review Report
Executive Summary
This is a substantial, well-structured refactoring that splits the monolithic inspect_build tool into two focused tools query_build + wait_build, unifies the outcome enums, and bumps the CAS cache version. The changes are internally
consistent across 42 files spanning production code, tests, specs, skills, and documentation. The architectural direction is sound â€” separating query concerns from blocking-wait concerns is a clear improvement. However, there are
several areas of concern around the wait_build API contract, concurrency safety in the CAS cleanup, and some inconsistencies in the test assertions.

ðŸ”´ Critical Must Address

1. [Risk] [Mistake]: wait_build requires buildId and timeout as required parameters â€” The auto-generated LOOKUP_TOOLS.md shows buildId and timeout as required fields for wait_build. This means every call to wait_build must specify a
   timeout, even when the user wants to wait indefinitely. This is a breaking UX change from the old inspect_build behavior where timeout was optional.
    1. Rationale: Making timeout required forces callers to guess an upper bound, which can lead to premature timeouts on slow builds or excessively long waits on fast builds. The old pattern of "no timeout = wait forever" was more
       flexible.
    2. Recommendation: Consider making timeout optional with a large default e.g., 10 minutes or supporting a sentinel value e.g., -1 for indefinite wait. Update the JSON schema accordingly.
2. [Concurrency] [Mistake]: cleanupOldCasVersions() uses CoroutineScope(Dispatchers.IO).launch as a fire-and-forget coroutine â€” In SourceStorageService.kt, the cleanup is launched in a new CoroutineScope with no structured concurrency, no
   error handling beyond a catch-and-log, and no mechanism to ensure it completes before the service is used.
    1. Rationale: Fire-and-forget coroutines in a service initializer can lead to race conditions where the cleanup is still deleting old CAS directories while new requests are trying to read from them. The CoroutineScope is also not tied
       to any lifecycle, so it could outlive the service.
    2. Recommendation: Either a run cleanup synchronously in the init block it's a startup cost that should be paid once, or b use ApplicationScope or a lifecycle-scoped coroutine, and add a synchronization mechanism e.g., a CountDownLatch
       or CompletableDeferred to ensure cleanup completes before the first read request is processed.
3. [Interface Contract] [Mistake]: wait_build API contract is ambiguous about what happens when no wait condition is provided â€” The spec says "If no specific wait condition regex or task is provided to wait_build, it SHALL default to
   waiting for the build to finish." However, the wait_build tool signature requires both buildId and timeout, and it's unclear from the diff whether the implementation actually supports the "wait for finish" default when neither
   waitForTask nor waitForRegex is provided.
    1. Rationale: The spec and implementation must be in sync. If the implementation doesn't handle the "no condition" case, the spec is misleading.
    2. Recommendation: Verify that wait_build(buildId="ID", timeout=60) without waitForTask or waitForRegex correctly waits for the build to finish. Add a test for this case.

ðŸŸ¡ Major Highly Recommended

1. [Logic] [Intentional â€” Flawed]: BuildComponentOutcome.PASSED replaces both TaskOutcome.SUCCESS and TestOutcome.PASSED â€” While unification is good, the name PASSED is semantically awkward for tasks. Tasks "succeed" or "complete";
   tests "pass". Using PASSED for a task outcome like :app:compileJava reads unnaturally.
    1. Intent acknowledged: The goal was to have a single enum for filtering both tasks and tests in query_build.
    2. Rationale: The spec says "Filter success tasks" should use outcome="PASSED", which is semantically confusing. A task that compiled successfully didn't "pass" â€” it "succeeded".
    3. Recommendation: Consider keeping SUCCESS as an alias or renaming to COMPLETED/SUCCEEDED which works better for both tasks and tests. Alternatively, keep the enum unified but use SUCCESS as the primary name and add a PASSED alias for
       test contexts.
2. [Logic] [Unclear Intent]: query_build with no arguments returns the dashboard, but the old inspect_build with no arguments also returned the dashboard â€” The diff shows McpToolWorkflowsTest.kt calling ToolNames.QUERY_BUILD with
   emptyMap() and asserting it returns results. However, the query_build tool's primary purpose is querying specific builds. Having it serve double duty as a dashboard tool when called with no arguments is an implicit design choice that
   should be explicit.
    1. Rationale: If query_build() no args returns the dashboard, then the dashboard is essentially a "list all builds" query. This is reasonable but should be documented as such.
    2. Recommendation: Document this behavior explicitly in the tool description and in the spec. Consider whether a separate list_builds or build_dashboard tool would be cleaner.
3. [Risk] [Mistake]: The wait_build tool's waitForTask parameter may have a race condition with task completion â€” If a task completes between the time the user calls wait_build and the time the wait logic starts monitoring, the wait may
   never be satisfied and will time out.
    1. Rationale: This is a classic "missed event" race condition. The old inspect_build likely had the same issue, but splitting it into a separate wait_build tool makes the race more visible.
    2. Recommendation: The wait_build implementation should first check if the condition is already met before starting to wait. Add a test that verifies this "already satisfied" case.
4. [Logic] [Unclear Intent]: query_build with kind="DASHBOARD" vs no arguments â€” The test GradleBuildLookupOutputFileTest uses kind="DASHBOARD" in some calls and no arguments in others. It's unclear whether kind="DASHBOARD" is a valid
   parameter or if the dashboard is only the default when no buildId is provided.
    1. Rationale: The test at line 63 uses kind="DASHBOARD" with a buildId, which seems contradictory â€” a dashboard is a summary view, not a per-build detail view.
    2. Recommendation: Clarify the semantics: either kind="DASHBOARD" is only valid without a buildId, or it's an alias for the summary view. Update tests and documentation accordingly.

ðŸ”µ Minor Polishing/Idioms

1. [Clean Code] [Mistake]: GradleExecutionTools.kt has a redundant !! removal that changes semantics â€” The diff shows val captureTaskOutput = it.captureTaskOutput!! changed to val captureTaskOutput = it.captureTaskOutput removing the !!.
   However, the variable is then used in it.commandLine.contains(captureTaskOutput) where captureTaskOutput is now nullable String?, and contains accepts Any?. This compiles but silently allows null to match any command line.
    1. Rationale: The original !! was intentional â€” it asserted non-null because the surrounding code already checked for null. Removing it introduces a subtle bug where a null captureTaskOutput could match unexpectedly.
    2. Recommendation: Keep the !! or add an explicit null check before the contains call.
2. [Idiom] [Mistake]: BuildComponentOutcome enum values use inconsistent naming conventions â€” The enum mixes SCREAMING_SNAKE_CASE UP_TO_DATE, FROM_CACHE, NO_SOURCE with values that look like past participles PASSED, FAILED, SKIPPED,
   CANCELLED. The old TaskOutcome used SCREAMING_SNAKE_CASE consistently.
    1. Rationale: Kotlin enum conventions typically use SCREAMING_SNAKE_CASE. The new values PASSED, FAILED, SKIPPED, CANCELLED, IN_PROGRESS break this convention.
    2. Recommendation: Either rename to SCREAMING_SNAKE_CASE e.g., PASSED â†’ SUCCESS or COMPLETED or document that this is intentional for readability in MCP tool output.
3. [Documentation] [Mistake]: running_gradle_tests/SKILL.md still references testName parameter â€” The skill doc says "You can provide a unique prefix of the test name e.g., testName=\"com.example.MyTest\" instead of the full FQN". This
   should reference the query parameter, not testName.
    1. Rationale: The old inspect_build had a testName parameter. The new query_build uses query for this purpose. The skill doc was partially updated but this reference was missed.
    2. Recommendation: Change testName="com.example.MyTest" to query="com.example.MyTest" in the skill doc.
4. [Documentation] [Mistake]: running_gradle_tests/SKILL.md references testOutcome parameter â€” The skill doc says testOutcome="FAILED" but the new API likely uses outcome="FAILED" based on the spec.
    1. Rationale: The spec says query_build(kind="TESTS", outcome="FAILED"), but the skill doc still uses the old testOutcome parameter name.
    2. Recommendation: Update the skill doc to use outcome instead of testOutcome.
5. [Documentation] [Mistake]: running_gradle_builds/SKILL.md still references taskPath parameter â€” The skill doc says taskPath=":app:compile" for unique prefix matching. The new API uses query for task lookup too.
    1. Rationale: The old inspect_build had a taskPath parameter. The new query_build uses kind="TASKS" with query for this.
    2. Recommendation: Update the skill doc to use kind="TASKS" with query instead of taskPath.
6. [Clean Code] [Polishing]: SourceStorageService.kt cleanup uses CoroutineScope(Dispatchers.IO).launch â€” the Dispatchers.IO is redundant â€” CoroutineScope(Dispatchers.IO) creates a new scope on the IO dispatcher, but launch inside it
   inherits the dispatcher. This is fine functionally but is a minor code smell.
    1. Rationale: A more idiomatic approach would be CoroutineScope(Dispatchers.Default).launch for CPU-bound file operations, or better yet, structured concurrency.
    2. Recommendation: Consider using Dispatchers.Default for the file tree walking which is CPU-bound, not I/O-bound or restructure to use structured concurrency.
7. [Test] [Mistake]: McpToolWorkflowsTest.kt asserts "Running" instead of "BUILD IN PROGRESS" â€” The test was changed from assertContains(statusText, "BUILD IN PROGRESS") to assertContains(statusText, "Running"). This is a weaker assertion
   that could match unintended text.
    1. Rationale: The string "Running" is more generic than "BUILD IN PROGRESS" and could appear in other parts of the output e.g., "Running build", "Task running".
    2. Recommendation: Either keep the original stronger assertion or use a more specific substring like "Status: Running" or "Build Status: Running".

â“ Open Questions for the Author
â€¢ [Interface Contract] [Unclear Intent]: The wait_build tool's JSON schema shows buildId and timeout as required. Was this intentional, or should timeout be optional with a default? If required, what is the expected behavior when a user
doesn't know how long the build will take?
â€¢ [Logic] [Unclear Intent]: The query_build tool supports kind="DASHBOARD" as a parameter. Is this a distinct kind from the default no-args behavior, or is it an alias? Can kind="DASHBOARD" be combined with a buildId?
â€¢ [Architecture] [Unclear Intent]: The BuildComponentOutcome enum uses PASSED for both task success and test passing. Was there consideration of keeping SUCCESS as the primary name and adding PASSED as a secondary filter option for
backward compatibility?
â€¢ [Concurrency] [Unclear Intent]: The cleanupOldCasVersions() in SourceStorageService.kt runs asynchronously in init. Is there a risk that a concurrent request arrives before cleanup completes and tries to read from a CAS directory that's
being deleted? How is this synchronized?

âœ… Positive Observations
â€¢ Comprehensive refactoring: All 42 files were updated consistently â€” production code, tests, specs, skills, and documentation all reflect the new query_build/wait_build split. This is excellent discipline.
â€¢ Clear architectural separation: Splitting inspect_build into query_build read-only queries and wait_build blocking waits follows the single-responsibility principle and makes the API more discoverable.
â€¢ Spec-driven development: The OpenSpec documents were created/updated alongside the code, providing clear requirements traceability.
â€¢ CAS versioning documentation: The CAS versioning contract is now well-documented in both AGENTS.md and the concurrency architecture doc, making the upgrade path clear for future maintainers.
â€¢ Test coverage: Tests were comprehensively updated to use the new tool names and API shapes, including integration tests for the wait/status flow.
â€¢ Backward compatibility hints: The GradleOutputs.kt changes provide helpful hints in the output directing users to the new tool names, easing the transition for LLM clients.

ðŸ“‹ Review Status
â€¢ Pass: 1
â€¢ Open questions remaining: 4
â€¢ Next step: Address the critical items wait_build timeout contract, CAS cleanup concurrency, wait_build default behavior before proceeding. The minor documentation issues can be fixed in a follow-up pass.