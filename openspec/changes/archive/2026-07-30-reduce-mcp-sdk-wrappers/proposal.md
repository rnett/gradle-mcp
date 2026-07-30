## Why
Kotlin MCP SDK 0.15.0 exposes the active request context and session primitives that the project still re-derives from `Server` and raw `CallToolRequest` values. Removing those redundant wrappers reduces duplicated protocol knowledge while preserving the project-owned tool DSL, conversion policy, progress UX, lifecycle, transport, and dependency-injection boundaries.

## What Changes
- Narrow `McpContext` to explicit `Json`, pre-resolved `ServerSession?`, `ClientConnection`, progress token, and captured `RequestHandlerExtra?` values.
- Capture SDK request context once in each tool handler and use it to correlate asynchronously queued progress notifications, with the session connection as a null-extra fallback.
- Resolve roots from the pre-resolved session while retaining the capability guard required before `ServerSession.listRoots()`.
- Delete unused elicitation, logging, auxiliary-content, and result-wrapper APIs without compatibility shims.
- Remove fixture elicitation capability setup and retain roots as the fixture's only client capability.
- Add behavior tests for no-roots clients, request-correlated async notifications, null-extra fallback, and `isError` result preservation.

## Capabilities
- `mcp-sdk-wrapper-reduction` is added.

## Impact
- Production: `McpContext.kt`, `McpServerComponent.kt`, and `GradleInputs.kt`.
- Tests and fixtures: `McpServerFixture.kt`, affected integration fixture configuration, and focused MCP context/server tests.
- Retained unchanged: `McpServerComponent`, `Registerer`, `Delegate`, `by tool`, schema generation, argument decoding, result/error conversion, component close hooks, the progress pipeline, `McpLifecycle.closeServer`, `Application.Transport`, local isolated Koin, the exact shared component list, and `McpFixtureClient`'s `Dispatchers.Default` request hop.
- No commit, push, archive, specification sync, or unrelated refactor is included.
