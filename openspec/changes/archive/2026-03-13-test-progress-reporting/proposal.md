## Why

Gradle builds often spend a significant amount of time executing tests, especially in larger projects. Currently, the progress reporting only shows which task is executing (e.g., `:app:test`), providing no visibility into how many tests
have passed, failed, or are remaining. Since Gradle discovers tests dynamically, we cannot know the total count upfront, but we can provide real-time counts of completed tests to give the user better feedback on build progress.

## What Changes

- **Test Status Tracking**: Enhance `DefaultGradleProvider.TestCollector` to maintain real-time counts of passed, failed, and skipped tests using atomic counters.
- **Progress Message Enhancement**: Update `RunningBuild.getProgressMessage` to append a test summary (e.g., `(12 passed, 2 failed)`) to the active task status when test events are detected.
- **Operation Monitoring**: Ensure `TestProgressEvent` is properly handled within the main progress loop to trigger UI updates immediately when a test finishes, even if the current task hasn't finished.

## Capabilities

### New Capabilities

- `test-progress-reporting`: Provides real-time test execution statistics within build progress notifications as they are discovered and executed.

## Impact

- **UX**: Significant improvement for interactive build monitoring.
- **Performance**: Negligible overhead from atomic counter increments.
- **API**: No breaking changes to existing MCP tool signatures; progress messages are internal to `RunningBuild`.
