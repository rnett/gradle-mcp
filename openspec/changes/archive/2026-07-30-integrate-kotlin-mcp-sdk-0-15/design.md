## Context
Before this change, the repository resolved Kotlin MCP SDK `0.14.0` through `mcpSdk`. `McpServer.kt` was an aggregate owner around the SDK `Server`: it propagated `ToolCallRequestId`, wrapped transports, tracked `activeToolCallJobs`, installed a custom `CancelledNotification` handler, launched detached tool work, overrode `connect`, maintained roots test state, and performed custom shutdown. This workaround only fully controlled stdio. Ktor SSE and Streamable HTTP create sessions through the SDK `createSession` path.

SDK `0.15.0` installs `RequestHandlerExtra` in the handler coroutine context, uses bounded per-connection handler jobs with 64 executing and 256 in-flight requests under an SDK `SupervisorJob`, matches built-in cancelled notifications by request ID, cancels the matching handler, suppresses the normal response, and clears and cancels handler state during teardown. Cancellation remains cooperative. The implementation now composes the SDK `Server` directly.

## Release Evidence and Traceability Matrix
Phase 0 was re-confirmed on 2026-07-29 against the official sources:
- Release: `https://github.com/modelcontextprotocol/kotlin-sdk/releases/tag/0.15.0`
- Compare: `https://github.com/modelcontextprotocol/kotlin-sdk/compare/0.14.0...0.15.0`
- PR #884: `https://github.com/modelcontextprotocol/kotlin-sdk/pull/884`
- PR #892: `https://github.com/modelcontextprotocol/kotlin-sdk/pull/892`
- PR #885: `https://github.com/modelcontextprotocol/kotlin-sdk/pull/885`

Confirmed symbols and behavior:
- `Protocol.DEFAULT_MAX_CONCURRENT_HANDLERS` is `64`, `DEFAULT_MAX_IN_FLIGHT_HANDLERS` is `256`, and `RequestHandlerExtra` carries `requestId` in the SDK handler context.
- `Protocol.handleCancelledNotification` looks up `Connection.inFlightRequestJobs` by request ID and cancels the matching job. Request dispatch suppresses normal and error responses when that handler job is cancelled.
- `Protocol.doClose` atomically clears in-flight handlers and the recently-cancelled ID buffer, then cancels `handlerScope`; it does not join handler jobs. PR #892 added the cancellation-buffer clear.
- `Server.createSession` connects the session, registers it in `ServerSessionRegistry`, and then invokes accumulated `onConnect` callbacks. `Server.close` closes every session and invokes `onClose` without a one-shot guard.
- `KtorServer.mcpSseEndpoint`, stateful Streamable HTTP, and stateless Streamable HTTP all call `Server.createSession`; a project `connect` override cannot govern every transport.
- `Client.addRoot`, `addRoots`, `removeRoot`, and `removeRoots` update the official client roots store. `ServerSession.listRoots` performs the session-local request round trip.
- PR #885 establishes create-session-before-client-connect ordering for `ChannelTransport`, which the fixture and direct SDK tests retain.

| SDK evidence or API gate | Local consequence | Implemented disposition |
|---|---|---|
| `RequestHandlerExtra` and SDK request IDs | Delete `ToolCallRequestId`, raw ID propagation, `wrapTransport`, and `activeToolCallJobs`. | Deleted with `McpServer.kt`; repository search is clean. |
| 64 executing and 256 in-flight handler jobs | Do not launch detached project tool jobs or maintain a second dispatcher. | Tool handlers execute inline; ChannelTransport concurrency is tested. |
| Built-in cancellation and response suppression | Delete custom `CancelledNotification` registration and preserve `runCatchingExceptCancellation`. | Deleted; ChannelTransport and real SSE cancellation tests pass. |
| PR #892 cancellation-buffer cleanup | Do not retain a project cancellation buffer or equivalent. | No project buffer exists. |
| PR #885 startup ordering | Create the server session before connecting a ChannelTransport client. | Fixture and direct tests use this order. |
| Ktor plugins use `createSession` | A project `connect` override cannot own all transports. | All transports resolve the direct SDK `Server`. |
| SDK session registry and `Server.close` | Resolve roots per matched session and close the SDK before components. | Implemented in `GradleInputs.resolve` and `McpLifecycle.closeServer`. |
| Official client roots APIs | Tests configure roots through the client and await tool responses. | Implemented without reflection, setters, or list-changed notifications. |

If any gate conflicts with this design, hold the dependent deletion and report the conflict.

## Direct Composition and Data Flow
`DI.kt` binds exactly one singleton `List<McpServerComponent>` and one SDK `Server` constructed and registered from that same list using explicit `Json`. All production DI and every test module and fixture SHALL bind or retain this same exact `List<McpServerComponent>` value; no second list factory, helper-internal DI lookup, holder, registry, or aggregate SHALL be used. `Application.resolveServer()` resolves that SDK type and logs and rethrows on failure. Stdio, SSE, and Streamable HTTP use the same resolved server. `UpdateTools.kt` calls the direct server construction path. Local isolated Koin contexts remain mandatory.

Roots handling is now on-demand. Per tool call, `server.sessions[clientConnection.sessionId]` is consulted. If `server.sessions[clientConnection.sessionId]` returns no matched session or `matchedSession.clientCapabilities?.roots == null`, it returns `null`; otherwise it invokes that matched session's `listRoots()`, maps an empty response to `emptySet()`, and propagates transient failures as tool errors. `GradleProjectRootInput.resolve()` and `GradleDocsTools.resolveVersion` are now suspend and delegate to pure `resolveRoot(roots: Set<Root>?)`, preserving current env fallback, implicit/single/multiple, explicit name, containment, and null-explicit semantics. Fixtures use SDK `Client.addRoot/addRoots/removeRoot/removeRoots` without `sendRootsListChanged()`. The project SHALL not use `RootsListChangedNotification`, StateFlow/cache/state, setters, update methods, or any project roots scope.

## SDK-First Teardown
`McpLifecycle.closeServer` is the shared helper used by `Application` and the test fixture. It follows an SDK-first order: it calls `Server.close()` first to stop sessions and initiate cooperative cancellation of handler jobs, then performs sequential list-order best-effort closes of every `McpServerComponent` with per-component exception isolation.
SDK close cancels but does not join handler jobs and has no internal timeout; `Server.close()` has no one-shot guard and may repeat `onClose`. Repeated calls are operationally safe because there are no custom callbacks and components are repeat-safe, but it is NOT guaranteed to be exactly-once. The helper return is not proof of handler join; tests separately await a cancellation signal. An optional cooperative timeout is permitted but cannot claim to abandon blocking non-cooperative work.

## Decisions and Rejected Alternatives
- Delete `McpServer.kt` completely. No reduced subclass, `McpRuntime`, `ServerHolder`, or aggregate owner.
- Execute tool handlers inline in the SDK request handler and preserve `runCatchingExceptCancellation` so `CancellationException` reaches SDK cancellation.
- Keep `McpServerComponent` as the tool grouping boundary, not as a lifecycle aggregate.
- Use official client roots APIs in tests. No reflection, server setter, or production-only DI seam.
- Require a real Ktor SSE cancellation test. Streamable HTTP cancellation remains optional follow-up.
- Treat duplicate registration and invalid params `-32602` as incidental upstream behavior.

## Test Mechanics
Write behavior tests before workaround deletion. A ChannelTransport test starts slow and quick calls on one session and uses deferred milestones, not sleeps, to prove four points: (1) the quick call completes while the slow call is suspended; (2) cancelling the slow call leaves the completed quick call successful; (3) a subsequent independent quick call succeeds; and (4) the slow handler observes cooperative cancellation. A second ChannelTransport test closes an active session and proves cancellation is initiated.

`SseCancellationE2ETest` follows existing `SseStartupE2ETest` patterns with a real Ktor server, SDK `Client`, `SseClientTransport`, and `CompletableDeferred`. It cancels after the handler reports active, asserts cooperative cancellation and absence of a normal result, then closes client, session, server, and components deterministically. Roots coverage uses SDK client roots APIs and requires multi-client session isolation, accepting per-call round trips.

## File and Symbol Mapping
- `gradle/libs.versions.toml`: bump `mcpSdk` to `0.15.0`.
- `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpServer.kt`: delete the entire file.
- `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpLifecycle.kt`: add `closeServer(server: Server, components: List<McpServerComponent>)`.
- `src/main/kotlin/dev/rnett/gradle/mcp/di/McpServerComponent.kt`: retain tool grouping, pass explicit context, and remove detached launch.
- `src/main/kotlin/dev/rnett/gradle/mcp/mcp/McpContext.kt`, `GradleInputs.kt`, `di/DI.kt`, `Application.kt`, `UpdateTools.kt`: compose, resolve, consume, and close the SDK server directly.
- `McpServerFixture`, `BaseMcpServerTest`, `GradleInputsTest`, `DIE2ETest`, and all root-setting callers: migrate to official client roots and shared lifecycle.
- `openspec/changes/.../specs/mcp-server-composition/spec.md`: add the normative direct-composition capability.
- `openspec/changes/.../specs/post-upgrade-hygiene/spec.md` and `specs/mcp-test-infrastructure/spec.md`: restate the complete modified requirements.
- `build-output-concurrency` documentation and generated tool metadata: update and review during implementation.

## Risks, Rollback, and Verification
The primary risks are incorrect assumptions about SDK session registration, `Server.close`, cooperative cancellation, or roots APIs. Phase 0 is a hard gate. Tests must use deferred milestones and bounded waits, and cleanup must close client/session/server and components.

Rollback is one cohesive revert of version bump, direct composition, deleted and added source, test migration, generated tool changes, and synchronized specifications.

## Verification Evidence
Verified on 2026-07-29:
- `:compileTestFixturesKotlin :compileTestKotlin :compileIntegrationTestKotlin`: successful.
- Focused fixture regressions (`McpServerBasicTest`, `GradleDocsVersionDetectionTest`, `GradleExecutionToolTest`, and `DependencySourceToolsTest`): 28 passed, 0 failed.
- `test integrationTest`: 493 passed, 1 skipped, 0 failed; build successful.
- `:updateToolsList :check`: successful; 21 executed tests passed and generated tool metadata had no upgrade-caused tracked diff.
- `openspec validate integrate-kotlin-mcp-sdk-0-15 --type change --strict --no-interactive`: change valid.
- Repository searches found no remaining `ToolCallRequestId`, active-job registry, transport wrapper, root setter, or project aggregate `McpServer` owner.
- No commit, push, PR mutation, archive, or spec synchronization was performed; governance task 6.2 remains deferred until this change merges to `main` with green verification.
