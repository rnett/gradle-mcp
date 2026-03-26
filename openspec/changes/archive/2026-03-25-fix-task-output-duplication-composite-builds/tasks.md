## 1. Fix Init Script

- [x] 1.1 In `task-out.init.gradle.kts`, wrap the entire listener-registration block with `if (gradle.parent == null)` so listeners are only registered for the root build in a composite

## 2. Test Fixture Enhancement

- [x] 2.1 Add `includeBuild(path, builder)` support to `GradleProjectBuilder` in `GradleProjectFixture.kt`, using the existing `file()` DSL to create the included build's `settings.gradle.kts` and `build.gradle.kts` under a subdirectory,
  and appending `includeBuild("<dir>")` to the root `settings.gradle.kts`

## 3. Tests

- [x] 3.1 Add a test in `TaskOutInitScriptTest` verifying that task output is NOT duplicated when one included build is present (assert the captured line count matches expected count)
- [x] 3.2 Add a test verifying no duplication with two included builds
