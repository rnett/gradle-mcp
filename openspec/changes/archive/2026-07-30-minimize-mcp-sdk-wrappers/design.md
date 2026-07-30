# Design: Minimize MCP SDK Wrappers

## Context
The existing bridge combined SDK request values, project services, mutable error state, roots lookup, progress, and JSON in context aggregates. The final design keeps SDK adaptation in `McpServerComponent`, gives handlers typed input and output, and makes progress the only project-owned per-call resource.

## Goals and Non-Goals
### Goals
- Make handler input, output, error state, and per-call dependencies explicit and typed.
- Guarantee progress cleanup for success, failure, cancellation, and decode failure.
- Preserve bounded, sampled, animated, correlated progress and notification backpressure behavior.
- Remove roots support completely while retaining deterministic explicit/environment project-root resolution.
- Preserve component grouping, lifecycle, schema, decoding, exception, conversion, transport, Gradle connection, and isolated dependency-injection boundaries.

### Non-Goals
- Changing the MCP SDK version.
- Flattening component registration or changing the shared component list.
- Changing application transport ownership or introducing global Koin state.
- Providing compatibility shims or a replacement roots abstraction.
- Modifying prior changes, reports, or archives.

## Decisions
### Typed Handler Boundary
`McpServerComponent.tool<I, O>` decodes `I` in the SDK adapter and invokes a typed handler with `ProgressReporter`. `ToolCallResult<O>` carries output and the handler's error state. `CallToolRequest` and JSON serialization remain adapter concerns; `McpContext` retains only schema conversion and the shared tool logger helpers.

### Adapter Ordering and Failure Semantics
The SDK adapter captures `RequestHandlerExtra?` exactly once before suspension, constructs the per-call `ProgressNotificationPipeline`, then decodes input, invokes the handler, and applies the converter inside `try`. It rethrows `CancellationException`, converts other exceptions directly to text error results, and closes the pipeline in `finally`.

The registration-time converter handles `Unit` and null as empty content, `String` as text content, direct `CallToolResult` passthrough with OR-ed error state, and structured output through the serializer captured for `O`. It performs no runtime `Any?` result guessing and no per-call serializer lookup.

### Progress Ownership
`ProgressNotificationPipeline` owns per-call progress and generic notification delivery. Its lazy `val progressReporter: ProgressReporter` is the public progress send surface; nested services receive only `ProgressReporter`, including `McpGradleHelpers.doBuild` through `context(ProgressReporter)`.

The pipeline uses bounded channels with `DROP_OLDEST`: notification capacity is replay `0` plus extra capacity `500`, and progress capacity is replay `10` plus extra capacity `50`. Normal dispatched coroutine launches, `trySend`, and a single notification collector preserve enqueue order and serialized delivery through exactly `extra?.sendNotification(notification) ?: clientConnection.notification(notification)`. Progress retains four-step `transformLatest` animation at 500 ms intervals and 100 ms sampling unless disabled.

`emitProgressNotification` bypasses sampling and animation, requires a progress token, and is a no-op after close. `emitNotification` accepts generic notifications without a progress token and is also a no-op after close. Delivery failures are isolated to the collector while cancellation propagates.

`close()` is non-suspending and idempotent. It marks the pipeline closed before cancelling channels and scope, does not join or wait, drops queued notifications, and rejects new sends. At most one notification already dequeued by the collector may finish after `close()` returns. Handler-finally ownership closes the pipeline on normal return, handler exception, cancellation, and decode failure.

### Roots Removal and Project-Root Resolution
All roots helpers, session lookup, capability logic, roots service, and root-listing behavior are deleted without a replacement abstraction. Fixtures lose default roots capability/state and roots mutators; roots-only tests are deleted, while unrelated tests use explicit or environment roots.

`GradleProjectRootInput` resolves a nonblank explicit path first, then a nonblank `GRADLE_MCP_PROJECT_ROOT` value, expanding, resolving, and normalizing the selected path. If neither is present, it throws a clear `IllegalArgumentException` instructing the caller to provide `projectRoot` or set `GRADLE_MCP_PROJECT_ROOT`.

### Retained Boundaries
`McpServerComponent` continues to own component grouping and lifecycle, tool registration, input schema and decoding, exception handling, and result conversion. SDK-first close, application transport ownership, local isolated Koin, the exact shared component list, the fixture client's real-dispatcher request hop, and `GradleConnectionService` remain intact.

## Risks and Controls
- Registration-time conversion could diverge by output kind; focused tests cover string, unit, null, structured, and direct SDK results with both error states.
- Pipeline shutdown could leak or emit late notifications; deterministic lifecycle tests cover every exit path and post-close sends.
- Fixture cleanup could accidentally remove unrelated test behavior; roots-only tests are deleted, while unrelated tests are migrated to explicit/environment roots.
