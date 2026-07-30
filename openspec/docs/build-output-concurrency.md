| **Stdout delivery**    | Single Gradle Tooling API thread | One `LineEmittingWriter` instance, then `processStdoutLine` → `RunningBuild.addLogLine`, `addTaskOutput` | Tooling API forwards daemon stdout over a single connection thread |
| **Stderr delivery**    | Single Gradle Tooling API thread (separate from stdout)                             | One `LineEmittingWriter` instance, then `processStderrLine` → `RunningBuild.addLogLine`                  | Same, but a separate
thread for stderr |
| **Progress callbacks** | Gradle Tooling API callback thread(s) — **may be multi-threaded with `--parallel`** | `BuildProgressTracker`, `TestCollector`, `ProblemsAccumulator`, `RunningBuild.addTaskResult`             | Gradle does not
guarantee single-threaded callback delivery; parallel tasks may dispatch events concurrently |
| **MCP readers**        | Kotlin MCP SDK handler coroutine(s) | Nothing (read-only)                                                                              | MCP tool handlers (for example, `query_build`) read build state concurrently with ongoing writes |

### Why this matters

Written from:

- Progress callback thread(s) via `addTaskResult()` (when `TaskFinishEvent` fires)
- MCP reads via `query_build`, which may overlap those callbacks

`ConcurrentHashMap` provides thread-safe `put` and iteration. `computeIfAbsent` is used where lazy initialization is needed (never `getOrPut`, which is not atomic).

Kotlin MCP SDK 0.15.0 dispatches post-initialization request handlers concurrently with per-connection bounds. Project tool handlers execute inline in those SDK-owned request coroutines; the project does not wrap transports, launch detached tool jobs, or maintain a parallel active-handler registry.

**Why:** A long-running tool call such as `gradle()` waiting on `awaitFinished()` must not block later requests such as `query_build()` on the same session.

**How:** The SDK owns request identity, bounded dispatch, `notifications/cancelled` matching, cooperative handler cancellation, and response suppression. The project preserves cancellation by allowing `CancellationException` to escape tool handlers.
