## 1. Core Model Changes

- [x] 1.1 Add `totalItems` and `completedItems` fields to `RunningBuild.kt` as AtomicLongs.
- [x] 1.2 Add `currentPhase` field to `RunningBuild.kt`.
- [x] 1.3 Implement `onPhaseStart(phase: String, total: Int)` and `onItemFinish()` in `RunningBuild.kt` to reset/update counters.

## 2. `GradleProvider` Implementation

- [x] 2.1 Update `GradleProvider` interface methods (`runBuild`, `runTests`, `getBuildModel`) to accept an optional `progressHandler`.
- [x] 2.2 Update `DefaultGradleProvider.kt` to add a `ProgressListener` for `OperationType.BUILD_PHASE` and `OperationType.GENERIC`.
- [x] 2.3 Handle `BuildPhaseStartEvent` to reset `completedItems` and update `totalItems` and `currentPhase` in `RunningBuild`.
- [x] 2.4 Handle `StatusEvent` and item finish events to update `RunningBuild` and invoke the `progressHandler`.

## 3. Progress Reporting Utilities

- [x] 3.1 Refactor `withProgressEmissions` in `McpGradleHelpers.kt` to provide both `progress` and `message` lambdas and maintain a sampled `ProgressState`.
- [x] 3.2 Update `doBuild` and other helpers in `McpGradleHelpers.kt` to pass the `progress` and `message` lambdas to `runBuild`.
- [x] 3.3 Ensure the `[CONFIGURING]` and `[EXECUTING]` prefixes are applied correctly in the reported message.

## 4. Verification

- [x] 4.1 Create a test case to verify 0-100% progress reporting for Configuration and Execution phases separately.
- [x] 4.2 Verify `[PHASE]` prefixes are present in progress notifications.
- [x] 4.3 Verify the progress bar resets to 0% at the start of the execution phase.
- [x] 4.4 Run `./gradlew :check` to ensure project integrity.
