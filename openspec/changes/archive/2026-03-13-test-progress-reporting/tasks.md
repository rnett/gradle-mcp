## 1. TestCollector Enhancements

- [x] 1.1 Add `AtomicLong` counters to `DefaultGradleProvider.TestCollector` for `passedTests`, `failedTests`, `skippedTests`, and `totalTests`.
- [x] 1.2 Update `TestCollector.statusChanged` to increment counters on `TestStartEvent` and `TestFinishEvent`.

## 2. RunningBuild Updates

- [x] 2.1 Add test counter exposure to `RunningBuild` (e.g., via `testResultsInternal` or new properties).
- [x] 2.2 Update `RunningBuild.getProgressMessage` to append test summary string `(P/F/S tests)` when tests have been detected.

## 3. GradleProvider Integration

- [x] 3.1 In `DefaultGradleProvider.executeBuild`, add a new `addProgressListener` for `OperationType.TEST` that invokes the `progressHandler` on `TestFinishEvent`.

## 4. Verification

- [x] 4.1 Update `GradleProgressIntegrationTest.kt` or add a new test to verify test progress messages are emitted.
- [x] 4.2 Run `./gradlew check` to verify the complete project.
