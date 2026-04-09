# Build Output Pipeline: Concurrency Model

This document describes the concurrency architecture of the Gradle build output processing pipeline, covering the threading model, which concurrency controls are necessary vs. unnecessary, and the rationale behind each decision.

---

## The Four Thread Contexts

When a Gradle build runs via the Tooling API, four distinct thread contexts interact with shared state:

| Context                | Thread(s)                                                                           | Writes to                                                                                                | Why this threading model                                                                                     |
|:-----------------------|:------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------|
| **Stdout delivery**    | Single Gradle Tooling API thread                                                    | One `LineEmittingWriter` instance, then `processStdoutLine` → `RunningBuild.addLogLine`, `addTaskOutput` | Tooling API forwards daemon stdout over a single connection thread                                           |
| **Stderr delivery**    | Single Gradle Tooling API thread (separate from stdout)                             | One `LineEmittingWriter` instance, then `processStderrLine` → `RunningBuild.addLogLine`                  | Same, but a separate thread for stderr                                                                       |
| **Progress callbacks** | Gradle Tooling API callback thread(s) — **may be multi-threaded with `--parallel`** | `BuildProgressTracker`, `TestCollector`, `ProblemsAccumulator`, `RunningBuild.addTaskResult`             | Gradle does not guarantee single-threaded callback delivery; parallel tasks may dispatch events concurrently |
| **MCP readers**        | Coroutine dispatcher threads                                                        | Nothing (read-only)                                                                                      | MCP tool handlers (e.g. `inspect_build`) read build state concurrently with ongoing writes                   |

### Why this matters

The threading model determines which concurrency primitives are necessary:

- **Single-writer, no concurrent readers** → No concurrency control needed (thread confinement)
- **Single-writer + concurrent readers** → Need visibility guarantees (`@Volatile`, `StringBuffer.toString()`, or concurrent collections)
- **Multiple writers + concurrent readers** → Need thread-safe collections (`ConcurrentHashMap`, `StringBuffer`, etc.)
- **Multiple writers, no readers** → Need thread-safe collections for write correctness

---

## Where Concurrency Control is NOT Needed

### `LineEmittingWriter` — Thread-Confined

Each `LineEmittingWriter` instance is accessed by exactly one thread:

1. **During the build:** The Gradle Tooling API delivers stdout on one thread and stderr on another. Each stream gets its own `LineEmittingWriter` instance with its own `StringBuilder` buffer. No two threads ever access the same instance.

2. **After the build:** `flush()` and `close()` are called from the `finally` block in `invokeBuild()`, which runs *after* `invoker(launcher)` returns. Since `invoker(launcher)` is a blocking call to the Gradle Tooling API, the daemon has
   finished sending output by the time `finally` runs. No concurrent `write()` is possible.

**Therefore:** `LineEmittingWriter` uses plain `StringBuilder` (not `StringBuffer`), a plain `Boolean` for `lastWasCR` (not `AtomicBoolean`), and no `synchronized` blocks. This eliminates per-character synchronization overhead that would
otherwise fire on every byte of Gradle output.

### `pendingRawLine` in `BuildExecutionService.setupOutputRedirection` — Thread-Confined

The `pendingRawLine` variable is a local `var` in the `setupOutputRedirection` closure. It is only accessed from the `lineLogger` callback of the stdout `LineEmittingWriter`, which is thread-confined (see above). No locking is needed.

---

## Where Concurrency Control IS Needed (and Why)

### `RunningBuild.logBuffer` (`StringBuffer`) — Two Writer Threads

`addLogLine()` is called from both the stdout delivery thread (via `processStdoutLine`) and the stderr delivery thread (via `processStderrLine`). These are separate threads, so concurrent `appendLine` calls can interleave.

`StringBuffer` provides per-method synchronization, making each `appendLine` call atomic. This is sufficient because `addLogLine` performs exactly one `appendLine` — no compound operations.

**Why `StringBuffer` and not a simpler alternative:** `StringBuffer.toString()` also synchronizes, providing a consistent snapshot when MCP readers call `consoleOutput`. Replacing with `StringBuilder` + external locking would add complexity
for no benefit.

**Critical invariant:** `addLogLine` must remain a single-operation mutation. The old `replaceLastLogLine` (which performed `length` + `setLength` + `appendLine` as three separate calls) was removed precisely because compound `StringBuffer`
operations are NOT atomic.

### `RunningBuild.taskResults` (`ConcurrentHashMap`) — Multiple Writer Contexts

Written from:

- Progress callback thread(s) via `addTaskResult()` (when `TaskFinishEvent` fires)
- Could overlap with MCP reads via `inspect_build`

`ConcurrentHashMap` provides thread-safe `put` and iteration. `computeIfAbsent` is used where lazy initialization is needed (never `getOrPut`, which is not atomic).

### `RunningBuild.taskOutputsAccumulator` (`ConcurrentHashMap<String, StringBuffer>`) — Cross-Thread Writes

Written from the stdout delivery thread (via `addTaskOutput()`). The nested `StringBuffer` values are individually thread-safe for `append`. MCP readers access via `taskOutputs` which calls `mapValues { it.value.toString() }` (safe
snapshot).

### `TestCollector` — Designed for Parallel Test Execution

The `TestCollector` explicitly documents that it uses concurrent collections for "massive parallel test execution." Progress callbacks for test events (`TestFinishEvent`) may arrive from multiple Gradle worker threads simultaneously. All
maps, queues, and counters use `ConcurrentHashMap`, `ConcurrentLinkedQueue`, and `AtomicInt` respectively.

The `AtomicInt` counters (`_passedCount`, `_failedCount`, etc.) are explicitly documented as accepting temporary inconsistency — they are progress indicators, not authoritative counts.

### `BuildProgressTracker` — Callback Threading Uncertainty

Progress listener events (`TaskStartEvent`, `TaskFinishEvent`, `BuildPhaseStartEvent`, `StatusEvent`) may arrive from multiple threads when Gradle runs tasks in parallel. The tracker uses `ConcurrentHashMap`, `ConcurrentLinkedDeque`, and
`AtomicReference` to handle this.

### `ProblemsAccumulator` — Concurrent Callback Writes + MCP Reads

`add()` is called from progress callback thread(s). `aggregate()` is called from MCP reader coroutines. The set of occurrences per problem uses `ConcurrentHashMap.newKeySet()` which supports safe concurrent iteration (unlike
`Collections.synchronizedSet` which requires external synchronization during iteration).

### `FailureIndexer` — Concurrent Callback Writes + Recursive Reads

`index()` and `withIndex()` are called from the callback thread during `handleTaskFinish` and from `toFinishedBuild()`. `withIndex()` recurses for nested failure causes. `ConcurrentHashMap.computeIfAbsent` ensures atomic deduplication.

### `BuildManager` — Multiple Coroutine Contexts

Written from MCP tool handler coroutines (`registerBuild`, `storeResult`, `stopAndRemove`) and a background cleanup coroutine. `ConcurrentHashMap` handles concurrent access. `AtomicReference` with `compareAndSet` for `latestFinished`
prevents a cleanup coroutine from wiping a value concurrently stored by a tool handler.

### `@Volatile` Flags — Cross-Thread Visibility

- `RunningBuild.taskOutputCapturingFailed`: Written from stdout thread, read from MCP coroutines
- `RunningBuild.status`: Written from the thread that calls `finish()`, read from MCP coroutines and progress tracker
- `TestCollector.isCancelled`: Written from MCP coroutine (build cancellation), read from callback threads

Without `@Volatile`, the JVM is free to cache these values in CPU registers, making writes invisible to other threads indefinitely.

### `MutableSharedFlow` / `CompletableDeferred` — Coroutine Primitives

- `_logLines` and `_completingTasks` flows: Enable MCP tool handlers to stream log lines and task completions in real-time
- `finishedBuildDeferred`: Allows `awaitFinished()` to suspend until the build completes

These are kotlinx.coroutines primitives with built-in thread safety.

---

## Console Output Access Patterns

### Hot Path: Avoid Materializing All Lines

`Build.consoleOutput` returns a `CharSequence` (backed by `StringBuffer.toString()`). The derived `consoleOutputLines` property calls `.lines()`, which allocates a `String` for every line. For large builds (e.g. `--info` or `--debug`), this
can produce millions of objects and cause GC pressure or apparent hangs.

**Rule:** In hot paths (tail display, head display, regex search), scan `consoleOutput.toString()` directly using index-based scanning:

- **Tail:** Scan backwards from the end using `lastIndexOf('\n', ...)`
- **Head:** Scan forwards using `indexOf('\n', ...)`
- **Regex search:** Iterate line-by-line using `indexOf`/`substring` without collecting into a list

`consoleOutputLines` is acceptable in cold paths (tests, one-shot renders of small builds).

### Total Line Count

When a total line count is needed (e.g. for pagination metadata), count newline characters directly: `output.count { it == '\n' }`. This is O(n) in characters but allocates no `String` objects.

---

## MCP Message Processing Concurrency

The `McpServer` overrides `connect()` to launch each incoming JSON-RPC message handler in `server.scope` rather than processing messages sequentially on the transport thread.

**Why:** The `StdioServerTransport` processes messages one at a time. A long-running tool call (e.g. `gradle()` waiting on `awaitFinished()`) would block all subsequent messages — `inspect_build()` could not be processed until the build
finishes, defeating the purpose of background builds.

**How:** Each `tools/call` message is launched as a separate coroutine in `scope`. The JSON-RPC request ID is injected into the coroutine context via `ToolCallRequestId` for cancellation support.

---

## Common Anti-Patterns and How to Avoid Them

### 1. Compound Operations on `StringBuffer`

**Wrong:** `buf.setLength(buf.length - n); buf.appendLine(replacement)` — three separately-synchronized calls, not atomic as a group.
**Right:** Use a single `appendLine` call, or redesign to avoid the need for replacement (pending-line buffer pattern).

### 2. `getOrPut` on `ConcurrentHashMap`

**Wrong:** `concurrentMap.getOrPut(key) { createValue() }` — not atomic, races possible.
**Right:** `concurrentMap.computeIfAbsent(key) { createValue() }` — single atomic operation.

### 3. `store(null)` on `AtomicReference` for Cleanup

**Wrong:** `atomicRef.store(null)` — wipes a concurrently-stored newer value.
**Right:** `val current = atomicRef.load(); if (current?.id == targetId) atomicRef.compareAndSet(current, null)` — only clears if the expected value is still there.

### 4. Materializing `consoleOutputLines` in Hot Paths

**Wrong:** `build.consoleOutputLines.takeLast(n)` — allocates all lines then discards most.
**Right:** Scan from the end of `consoleOutput.toString()` using `lastIndexOf('\n', ...)`.

### 5. `Collections.synchronizedSet` with Unguarded Iteration

**Wrong:** `Collections.synchronizedSet(mutableSetOf())` then calling `.toList()` or iterating without `synchronized(set) { ... }` — throws `ConcurrentModificationException`.
**Right:** Use `ConcurrentHashMap.newKeySet()` which supports safe concurrent iteration, or explicitly synchronize on the set during iteration.

### 6. Adding `synchronized` to `LineEmittingWriter`

**Wrong:** Wrapping `LineEmittingWriter.write()` in `synchronized(buf)` — unnecessary overhead on every character.
**Right:** Each instance is thread-confined to a single Gradle output delivery thread. No synchronization needed.

### 7. Assuming Stderr Can Be Suppressed by Stdout Logic

**Wrong:** Asserting that both stdout and stderr raw lines are absent from `consoleOutput` when using task output capturing.
**Right:** Only stdout raw lines are suppressed (pending-line buffer is stdout-only). Stderr lines arrive on a separate stream and cannot be correlated with stdout structured replacements.

### 8. Sequential MCP Message Processing Blocking Concurrent Tools

**Wrong:** Processing MCP messages directly on the transport thread — one slow call blocks everything.
**Right:** Launch each message handler in a coroutine scope.

---

## Key Files

| File                        | Concurrency Role                                                                                                                                                               |
|:----------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LineEmittingWriter.kt`     | Thread-confined line buffering — NO synchronization needed                                                                                                                     |
| `BuildExecutionService.kt`  | `setupOutputRedirection` creates pending-line closure (thread-confined); `processStdoutLine`/`processStderrLine` handle stream-specific logic                                  |
| `RunningBuild.kt`           | Shared mutable state; uses `StringBuffer` (multi-writer), `ConcurrentHashMap` (multi-writer + reader), `@Volatile` (cross-thread flags), `MutableSharedFlow` (async streaming) |
| `BuildManager.kt`           | `ConcurrentHashMap` + `AtomicReference` with CAS for build lifecycle                                                                                                           |
| `BuildProgressTracker.kt`   | `ConcurrentHashMap` + `AtomicReference` for progress state (callback threading uncertain)                                                                                      |
| `TestCollector.kt`          | Full concurrent collection suite for parallel test execution                                                                                                                   |
| `ProblemsAccumulator.kt`    | `ConcurrentHashMap` with `ConcurrentHashMap.newKeySet()` for safe iteration                                                                                                    |
| `FailureIndexer.kt`         | `ConcurrentHashMap.computeIfAbsent` for thread-safe deduplication                                                                                                              |
| `McpServer.kt`              | `scope.launch` for concurrent message handling                                                                                                                                 |
| `GradleBuildLookupTools.kt` | Index-based scanning of `consoleOutput` to avoid line materialization                                                                                                          |
| `GradleOutputs.kt`          | Same scanning pattern for `toOutputString`                                                                                                                                     |
