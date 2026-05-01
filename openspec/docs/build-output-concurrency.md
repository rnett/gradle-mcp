| **Stdout delivery**    | Single Gradle Tooling API thread | One `LineEmittingWriter` instance, then `processStdoutLine` → `RunningBuild.addLogLine`, `addTaskOutput` | Tooling API forwards daemon stdout over a single connection thread |
| **Stderr delivery**    | Single Gradle Tooling API thread (separate from stdout)                             | One `LineEmittingWriter` instance, then `processStderrLine` → `RunningBuild.addLogLine`                  | Same, but a separate
thread for stderr |
| **Progress callbacks** | Gradle Tooling API callback thread(s) — **may be multi-threaded with `--parallel`** | `BuildProgressTracker`, `TestCollector`, `ProblemsAccumulator`, `RunningBuild.addTaskResult`             | Gradle does not
guarantee single-threaded callback delivery; parallel tasks may dispatch events concurrently |
| **MCP readers**        | Coroutine dispatcher threads | Nothing (read-only)                                                                                      | MCP tool handlers (e.g. `query_build`) read build state concurrently with
ongoing writes |
| **MCP readers**        | Coroutine dispatcher threads | Nothing (read-only)                                                                                      | MCP tool handlers (e.g. `query_build`) read build state concurrently with
ongoing writes |

### Why this matters

Written from:

- Progress callback thread(s) via `addTaskResult()` (when `TaskFinishEvent` fires)
- Could overlap with MCP reads via `query_build`
- Could overlap with MCP reads via `query_build`

`ConcurrentHashMap` provides thread-safe `put` and iteration. `computeIfAbsent` is used where lazy initialization is needed (never `getOrPut`, which is not atomic).

The `McpServer` overrides `connect()` to launch each incoming JSON-RPC message handler in `server.scope` rather than processing messages sequentially on the transport thread.

**Why:** The `StdioServerTransport` processes messages one at a time. A long-running tool call (e.g. `gradle()` waiting on `awaitFinished()`) would block all subsequent messages — `query_build()` could not be processed until the build
**Why:** The `StdioServerTransport` processes messages one at a time. A long-running tool call (e.g. `gradle()` waiting on `awaitFinished()`) would block all subsequent messages — `query_build()` could not be processed until the build
finishes, defeating the purpose of background builds.

**How:** Each `tools/call` message is launched as a separate coroutine in `scope`. The JSON-RPC request ID is injected into the coroutine context via `ToolCallRequestId` for cancellation support.
