---
name: gradle_mcp_concurrency_expert
description: >-
  Expert patterns for Kotlin Coroutines, Flow, and specialized locking mechanisms, including advanced async testing strategies within the Gradle MCP project.
metadata:
  author: rnett
  version: "1.0"
---

# Skill: Gradle MCP Concurrency Expert

This skill provides expert guidance on Kotlin Coroutines, Flow usage, and specialized locking mechanisms within the Gradle MCP project, including advanced patterns for testing asynchronous operations.

## Coroutine & Flow Usage

- **Parallel Source Processing**: Source extraction and indexing are performed in parallel using Kotlin `Flow` and `flatMapMerge`. This significantly improves performance but requires careful concurrency management (limiting global IO
  tasks).
- **Parallelism Strategy**: Prefer `flatMapMerge` (via `parallelMap`/`parallelForEach` utilities) for concurrency control over manual `Semaphore` management.
- **Naming Conventions**: Explicitly name non-deterministic parallelism utilities (e.g., `unorderedParallelMap`) to signal that input order is not preserved.
- **Flow Draining**: Always ensure that indexing operations consume the entire file flow, even if the index is already up-to-date, when using a `Channel` based extraction pipeline to prevent deadlocks.
- **Wait Policy**: ALWAYS use `runTest` for suspending tests, NEVER `runBlocking`.
- **Test Timeouts**: Always specify a generous timeout (e.g., 10 minutes) for integration tests using `runTest` that trigger Gradle builds or start external processes. This prevents flaky failures due to the default 60-second timeout being
  exceeded during parallel execution or on slow machines.

## Locking & Re-entrancy

- **Coroutine Re-entrancy**: Use an immutable linked-list of held locks in `CoroutineContext.Element` for thread-safe coroutine re-entrancy. This ensures child coroutines safely inherit held locks while maintaining strict isolation between
  parallel siblings.
- **Job Tracking**: Ensure `currentJob` is correctly tracked through `withContext` calls, as every `withContext` creates a new `Job`.
- **Lock Upgrades**: Explicitly detect and fail Shared-to-Exclusive lock upgrade attempts. NIO `FileLock` does not support upgrades within the same JVM process and will trigger an `OverlappingFileLockException`.
- **Multi-Lock Avoidance**: Avoid implementing utilities that acquire multiple file locks simultaneously (`withLocks`) in a coroutine environment, especially within tests, to reduce deadlock risks.
- **Lock Release Polling**: When using `FileLockManager.tryLockAdvisory` in a polling loop to wait for another process to release a lock, ALWAYS close the acquired lock (e.g., `lock?.close()`) immediately. This prevents the waiting process
  from accidentally blocking other siblings by holding the lock it is trying to observe.
- **Context Parameters & Mockk**: To mock functions with context parameters, use `any<ContextParamType>()` for each parameter in the `context` block.

## Testing Asynchronous Events

- **Deterministic Synchronization**: Avoid `delay()` or `Dispatchers.Unconfined` for synchronizing tests with background progress. Instead, use `CompletableDeferred<Unit>` as a "signal" that can be completed by the tracker and awaited by
  the test.
- **Lifecycle Management**: Use `backgroundScope` in `runTest` when creating objects that launch long-lived background coroutines (like `RunningBuild`).
- **Async Log Processing**: When implementing asynchronous log processing using a `Channel`, always use a `CompletableDeferred` to signal completion of the processing loop.
- **Test Hooks**: It is acceptable to add internal "onProgressFinished" or similar callback hooks to trackers specifically for test synchronization.

## Examples

### Testing a background progress tracker

1. Create a `CompletableDeferred<Unit>` as a signal.
2. In the tracker's `onProgressFinished` hook, complete the signal.
3. In the test, await the signal before asserting the final state.
4. Use `backgroundScope` for the build process to ensure clean termination.

### Mocking a function with context parameters

```kotlin
context(any<ProgressReporter>()) {
    myService.performTask(any())
}
```

Reasoning: Using `any()` with explicit types in the `context` block to resolve overloads for Mockk.
