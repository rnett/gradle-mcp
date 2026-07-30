# Tasks: Minimize MCP SDK Wrappers

## Phase 0: Apply Gate
- [x] Confirm the prerequisite wrapper-reduction capability has been synced or archived and exists in main.
- [x] Confirm the prerequisite capability requirements matched before apply.

## Phase 1: Typed Adapter and Progress Contract
- [x] Replace the call environment with typed handler inputs, `ProgressReporter`, and return-carried `ToolCallResult<O>`.
- [x] Add exact `ProgressNotificationPipeline` ownership with bounded `DROP_OLDEST` channels, four-step animation, sampling, serialized correlation, and invoking-connection fallback.
- [x] Make lazy `val progressReporter: ProgressReporter` the pipeline's public progress send surface and migrate `McpGradleHelpers.doBuild` to `context(ProgressReporter)`.
- [x] Add token-gated direct progress emission and token-independent generic notification emission; make all sends no-ops after close.
- [x] Make `close()` non-suspending and idempotent, mark closed before cancellation, drop queued notifications, and allow at most one already-dequeued notification to finish.
- [x] Migrate `McpServerComponent.tool<I, O>` while retaining input schema and decoder ownership and capturing the output converter and serializer at registration time.
- [x] Keep `CallToolRequest` adapter-only; capture extra once, decode, invoke, convert, rethrow cancellation, convert other exceptions to text errors, and close in `finally`.
- [x] Implement registration-time conversion for unit/null, string, direct `CallToolResult`, and structured output without runtime guessing or per-call serializer lookup.

## Phase 2: Context, JSON, and Roots Deletion
- [x] Delete production `McpContext`, `McpToolContext`, their per-call JSON, and handler dependencies on those aggregates; retain unrelated schema and logger helpers in `McpContext.kt`.
- [x] Delete production roots helpers, session lookup and capability branches, roots service, `listRoots` behavior, and replacement-root abstractions.
- [x] Implement `GradleProjectRootInput` precedence: normalize explicit input, then `GRADLE_MCP_PROJECT_ROOT`, otherwise throw a clear `IllegalArgumentException`.
- [x] Migrate all handlers to typed input/output and return-carried error state while preserving component grouping, lifecycle, schema, decode, exception, result conversion, transport, and Gradle connection boundaries.

## Phase 3: Fixtures, Tests, and Documentation
- [x] Delete fixture default roots capability/state and roots mutator support.
- [x] Delete roots-only tests; migrate unrelated tool tests to explicit or environment project roots without adding a roots abstraction.
- [x] Remove roots claims from current manual and generated tool documentation; leave archives untouched.
- [x] Add deterministic pipeline lifecycle, backpressure, routing, serialization, sampling, animation, cancellation, failure, decode, and post-close tests.
- [x] Add a representative nested-service propagation test proving only `ProgressReporter` reaches the caller.
- [x] Add project-root behavior tests for explicit and environment resolution plus missing-root failure, with static checks excluding roots, session, and capability types.
- [x] Add adapter tests for string, unit, null, structured serialization, direct `CallToolResult`, both error states, decode failure, non-cancellation exceptions, and cancellation propagation.

## Phase 4: Verification
- [x] Run targeted tests, then `:test :integrationTest`.
- [x] Run mandatory `:updateToolsList` after tool metadata/source changes, then `:check`.
- [x] Run strict OpenSpec validation and schema inspection.
- [x] Run targeted stale-heading, deleted-symbol, forbidden-concurrency, and result-conversion contradiction checks.
- [x] Run `git diff --check` and confirm only intended implementation, tests, current docs, generated metadata, and this change are modified.
