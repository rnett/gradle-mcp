## 1. State Management in `RunningBuild`

- [x] 1.1 Add `activeOperations` set to `RunningBuild.kt` to track current tasks/configs.
- [x] 1.2 Implement a method in `RunningBuild` to generate the descriptive progress message.

## 2. Update Progress Listeners

- [x] 2.1 Update `DefaultGradleProvider.kt` to add/remove operations from `activeOperations` on `TaskStartEvent` and `TaskFinishEvent`.
- [x] 2.2 Update `DefaultGradleProvider.kt` to add/remove operations from `activeOperations` on `ProjectConfigurationStartEvent` and `ProjectConfigurationFinishEvent`.
- [x] 2.3 Pass the enhanced progress message to the `progressHandler` in `DefaultGradleProvider.kt`.

## 3. Tool Cleanup

- [x] 3.1 Refactor `McpGradleHelpers.doBuild` to rely on the centralized message generation in `RunningBuild`.
- [x] 3.2 Ensure consistent phase prefixing across all progress emitters.

## 4. Verification

- [x] 4.1 Create a new test case in `GradleProgressIntegrationTest.kt` that uses a multi-task build (e.g., `help tasks`) and verifies the progress messages contain multiple tasks or more descriptive text.
- [x] 4.2 Run `./gradlew check` to ensure no regressions in build execution or progress reporting.
