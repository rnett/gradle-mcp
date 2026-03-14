## 1. Progress Reporting in `inspect_build`

- [x] 1.1 Identify or expose the progress flow from `RunningBuild` or `ProgressTracker`.
- [x] 1.2 Modify `InspectBuildTool`'s wait logic to launch a concurrent coroutine that collects from this flow and reports it via `McpContext`.
- [x] 1.3 Ensure progress collection is scoped correctly to the wait duration (stops when the tool returns or the wait condition is met).

## 2. Verification

- [x] 2.1 Add a test (in `BackgroundBuildStatusWaitTest` or similar) to verify progress notifications are emitted when `inspect_build` waits for a target task in an active background build.
- [x] 2.2 Ensure the test verifies that progress stops being reported once the wait condition is satisfied.
