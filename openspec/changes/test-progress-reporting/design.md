## Context

Build progress currently focuses on high-level task and configuration phase execution. While `TestResults` are collected internally via `TestCollector`, they are not reflected in the real-time build progress notifications emitted by the MCP
server. Users running long test suites currently only see which test task is active, without any indication of pass/fail counts or overall test progress.

## Goals / Non-Goals

**Goals:**

- Provide visual feedback on test execution progress within build progress notifications.
- Show counts of passed, failed, and skipped tests when a test task is active.
- Support both fixed-total (if reported by Gradle) and incremental progress displays.

**Non-Goals:**

- Showing individual test names in the main progress bar (this would be too noisy).
- Real-time display of test output (already handled by other mechanisms).

## Decisions

### 1. Test Execution Tracking

**Decision:** Enhance `DefaultGradleProvider.TestCollector` to maintain atomic counters for `passed`, `failed`, `skipped`, and `started` tests.
**Rationale:** Atomic counters provide a high-performance way to track counts without needing to iterate or lock the large lists of full test results during every progress update.

### 2. Triggering Progress Updates

**Decision:** Register a dedicated `ProgressListener` in `DefaultGradleProvider.executeBuild` specifically for `OperationType.TEST`.
**Rationale:** This avoids adding long-lived callbacks to `TestCollector`. By registering a listener within the build execution scope, we can directly invoke the `progressHandler` whenever a `TestFinishEvent` occurs, ensuring the progress
message is refreshed with the latest counts.

### 3. Progress Message Formatting

**Decision:** Update `RunningBuild.getProgressMessage` to append test statistics in parentheses.

- Format: `(pass: P, fail: F, skip: S)`
- Since total counts are not typically available upfront due to dynamic discovery, the summary will show absolute counts as they are completed.
  **Rationale:** This provides a concise, standard format that fits alongside the existing task progress information, giving immediate feedback on the test suite's health.

## Risks / Trade-offs

- **[Risk] High-Frequency Updates** → If tests are extremely fast, test progress updates might overwhelm the notification channel.
    - **Mitigation**: The MCP client/server protocol typically handles high-frequency notifications well, but we can implement a simple rate-limiter in the `progressHandler` if necessary.
- **[Risk] Total Count Accuracy** → Gradle doesn't always provide an accurate total count for all test runners.
    - **Mitigation**: Prefer showing passed/total when total is known, otherwise fallback to simple pass/fail/skip counts.
