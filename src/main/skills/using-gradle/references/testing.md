# Testing

Authored for AI agents. Provides directive patterns for executing and triaging Gradle tests.

## Test Execution Patterns

### Execution Modes
- **Filtered Run (Default)**: Always target specific classes or methods first to minimize noise and feedback latency.
- **Full Suite**: Use `check` or `test` only for final verification or when systemic failure is suspected.

**Anti-pattern**: Running the entire test suite to verify a local fix.

### Target Selection (`--tests`)
Use `--tests` to filter the test set.

| Selection Type | Syntax Example | Rule |
| :--- | :--- | :--- |
| **Single Class** | `--tests "com.example.UserTest"` | Use the fully qualified test name, not a file path. |
| **Single Method** | `--tests "com.example.UserTest.testLogin"` | Use `Class.method`. |
| **Wildcard Class** | `--tests "com.example.*Test"` | Match all classes ending in `Test` in the package. |
| **Wildcard Method** | `--tests "com.example.UserTest.*"` | Match all methods in the class. |
| **Multi-Filter** | `--tests "ClassA" --tests "ClassB"` | Multiple flags are additive. |
| **Zero-Match** | `--tests "NonExistent"` | May be green with 0 tests; treat zero executed tests as failure and inspect the report. |

**Version notes**: `--tests` filtering and wildcard behavior are stable across Gradle 7-9; do not assume a zero-match filter fails the task.

### Environment Caveats
- **Task selection is part of filtering**: Discover the actual test task path first. `test`, `check`, `testJvm`, `testAndroidUnitTest`, and connected/device tasks are not interchangeable; a valid `--tests` pattern on the wrong task can run zero relevant tests.
- **Kotlin Multiplatform (KMP)**: Target the discovered platform task (for example, `:app:testAndroid` versus `:app:testJvm`).
- **Android**: Distinguish `test` unit tests from `connectedCheck`/`androidTest` instrumented tests.
- Confirm the selected task's executed-test count and report before claiming a filtered run passed.

### Discovery and worker topology

Use `--test-dry-run` to verify that the intended tests are discovered before relying on a green task. **Wrapper check:** Gradle 9.0 can silently pass an empty discovery, so treat zero discovered tests as a failed verification even when the task is green. Set `forkEvery` and `maxParallelForks` deliberately for isolation and throughput; a low `forkEvery` is not a cure for flaky tests. Custom JVM test suites may not be wired into `check`, so inspect the task graph and run the suite task explicitly.

## Failure Investigation Workflow

### 1. Isolate and Identify
Run the target tests. If they fail, do NOT use `captureTaskOutput` or `taskPath` to retrieve failures.

### 2. Surgical Triage (Surgical Home)
Use `query_build` to extract the failure set:

1. **List Failures**: `query_build(kind="TESTS", outcome="FAILED")` $\rightarrow$ Get list of failed test FQNs.
2. **Extract Details**: `query_build(kind="TESTS", query="FQN_FROM_STEP_1")` $\rightarrow$ Get full stack trace and failure message.
3. **Analyze Context**: `query_build(kind="CONSOLE", buildId=ID)` $\rightarrow$ Inspect logs surrounding the failure for environment issues.

**Anti-pattern**: Parsing the raw console output to find failing tests.

### 3. Handling Name Collisions
If `query_build` returns multiple tests for a query, use `testIndex` (0-based) to select the specific instance.

## Rerunning Tests

### Targeted Forcing
Use `--rerun` to force the execution of a test and its dependencies, bypassing the cache.

**Version notes**: Gradle 9 and 8.x use `--rerun`; Gradle 7.6+ also supports and prefers `--rerun`. Gradle 7.0-7.5: use `cleanTest test` (if `cleanTest` exists) or `--rerun-tasks`.

**Anti-pattern**: Using `clean` as a default diagnostic for test failures; it destroys too much state.

**More info**: Test filtering and reports: `gradle_docs(path="userguide/java_testing.md")`; test diagnostics: `query_build(kind="TESTS")`.

Cross-references:
- Build execution lifecycle $\rightarrow$ [Running Builds](running-builds.md)
- General build failures and build scans $\rightarrow$ [Troubleshooting](troubleshooting.md)
