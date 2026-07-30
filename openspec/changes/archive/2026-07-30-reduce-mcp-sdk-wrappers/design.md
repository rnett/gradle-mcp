## Context
The completed Kotlin MCP SDK 0.15.0 integration already delegates dispatch, cancellation, session ownership, and transport handling to the SDK. A smaller follow-up remains: `McpContext` still carries the aggregate `Server` and raw `CallToolRequest`, looks up its session later, and sends queued notifications without the request correlation already available through the SDK handler context. It also contains unused elicitation, logging, and auxiliary-result APIs.

## SDK 0.15.0 Fact Matrix
| SDK fact | Source/API | Local consequence |
|---|---|---|
| One `RequestHandlerExtra` is installed in the active inbound handler coroutine context. | `Protocol.kt`: `RequestHandlerExtra`, `currentRequestHandlerExtra()` | Capture it in the SDK tool handler before creating the separately rooted progress scope. |
| `RequestHandlerExtra.sendNotification` sends with the active request ID as `relatedRequestId`. | `Protocol.kt`: `RequestHandlerExtra.sendNotification` | Prefer it for queued notifications so SSE and resumable transports retain request correlation. |
| `currentRequestHandlerExtra()` is nullable outside the active handler context. | `Protocol.kt`: `currentRequestHandlerExtra()` | Preserve `ClientConnection.notification` as the explicit fallback. |
| `Server.sessions` is a snapshot registry keyed by the invoking connection's session ID. | `Server.kt`: `sessions`; `ClientConnection.sessionId` | Resolve `ServerSession?` once in the handler and carry the result, not the aggregate server. |
| `ServerSession` owns negotiated client capabilities and delegates `listRoots`. | `ServerSession.kt`: `clientCapabilities`, `listRoots()` | Keep the roots capability guard because `listRoots()` throws when roots were not advertised. |
| `ClientConnection` is session-local. | `ClientConnection.kt` | The fallback remains isolated to the invoking client. |

## Request Context and Notification Flow
The SDK tool handler SHALL resolve `server.sessions[this.sessionId]`, `currentRequestHandlerExtra()`, and `request.meta?.progressToken` exactly once. `McpContext` SHALL carry those immutable values with the project `Json` and current `ClientConnection`.

The progress queue remains project-owned because its sampling, animation, backpressure, and lifecycle behavior have no SDK replacement. Its collector runs in a separate `Dispatchers.Default + SupervisorJob()` scope, so it SHALL use the captured extra rather than calling `currentRequestHandlerExtra()` from that scope. Delivery SHALL be `extra?.sendNotification(notification) ?: clientConnection.notification(notification)`.

Safety invariant: a queued notification is sent to the same client that invoked the tool, and uses the SDK request correlation when an active handler extra was captured. Liveness invariant: the existing queue and progress scope continue draining until the tool context closes; null request context does not prevent fallback delivery.

## Session-Local Roots
`GradleProjectRootInput.resolve()` SHALL use `ctx.session`. If the session is absent or `session.clientCapabilities?.roots` is null, roots resolve as `null` without calling `listRoots()`. Otherwise it performs the existing on-demand `session.listRoots()` round trip. No roots cache, global state, or cross-client lookup is introduced.

The fixture SHALL advertise roots only. Elicitation capability setup is vestigial because all custom elicitation APIs have zero callers and are removed. The fixture SHALL resolve the component list once and construct its SDK server directly from that same list, so factory-bound test components cannot diverge between registration and teardown.

## Retained Irreducible Boundaries
The refactor retains `McpServerComponent`, `Registerer`, `Delegate`, property-delegated `by tool` registration, schema generation, argument decoding, output conversion, error conversion, direct `CallToolResult` passthrough, component close hooks, and the custom progress pipeline. It also retains `McpLifecycle.closeServer`, `Application.Transport`, local isolated Koin contexts, the exact shared component list used for registration and close, and `McpFixtureClient`'s `Dispatchers.Default` request hop.

## Removed Dead APIs
The change removes `ElicitationResult`, `elicit`, `elicitUnit`, elicitation schema conversion, `emitLoggingNotification`, `McpToolContext.additionalResults`, `addAdditionalContent`, and `AuxiliaryResults`. There are no compatibility shims because repository callers do not exist and MCP tool consumers do not compile against these project APIs.

## Result Handling
`McpToolContext.isError` remains the single mutable result flag. Result conversion SHALL read `context.isError` directly for string, unit, and structured outputs. Existing exception conversion and direct `CallToolResult` passthrough remain unchanged.

## Rejected Alternatives
- Flattening all registrations into direct SDK `Server.addTool` calls is rejected. It would remove the component grouping and close ownership required by DI, lifecycle, schema policy, and the exact shared component list.
- Looking up `currentRequestHandlerExtra()` in the progress scope is rejected because that scope is independently rooted and does not inherit the SDK handler coroutine context.
- Caching roots is rejected because roots are client-owned, session-local, and intentionally queried on demand.
- Removing the client fixture wrapper is rejected because its real-dispatcher request hop prevents virtual-time request timeouts while preserving caller cancellation.

## Verification
Compile main, test fixtures, test, and integration-test source sets. Run focused MCP context, SDK integration, roots, progress, cancellation, and teardown tests; then run full `test integrationTest`, `:updateToolsList`, `:check`, strict OpenSpec validation, stale-symbol searches, and `git diff --check`.
