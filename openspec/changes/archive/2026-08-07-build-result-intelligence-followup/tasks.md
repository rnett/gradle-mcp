## 1. Verbatim reason contract

- [x] 1.1 Store a null `reason` for `FROM_CACHE`, `UP_TO_DATE`, `SUCCESS`, `FAILED`, and `CANCELLED` task results in `BuildExecutionService.handleTaskFinish`.
- [x] 1.2 Hold the verbatim Gradle skip message as `reason` for `SKIPPED` and `NO_SOURCE` outcomes, with no outcome prefix, and never collapse `NO_SOURCE` to `SKIPPED`.
- [x] 1.3 Print `Reason:` in `query_build kind=TASKS` output whenever `reason` is non-null.

## 2. Task origins only in TASKS output

- [x] 2.1 Render the `Task Origins:` section only in `getTasksOutput` (the `kind=TASKS` path).
- [x] 2.2 Ensure DASHBOARD, CONSOLE, and base `toOutputString` output do not include `Task Origins:`.

## 3. Verification

- [x] 3.1 Update and run focused unit tests asserting the verbatim reason strings and TASKS-only origin rendering.
- [x] 3.2 Run the targeted build-result tests, then the repository-required broader verification.
