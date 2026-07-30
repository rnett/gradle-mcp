# mcp-server-composition Specification

## Purpose
TBD - created by archiving change integrate-kotlin-mcp-sdk-0-15. Update Purpose after archive.
## Requirements
### Requirement: Direct SDK Server Composition
The application SHALL compose and resolve the Kotlin MCP SDK `Server` directly using exactly one `List<McpServerComponent>` value and the project's explicit `Json` value. The explicit `Json` SHALL be passed or used in direct SDK `Server` construction, component registration, and `McpContext` as appropriate. The same exact `List<McpServerComponent>` value SHALL be used both for server registration and as the input to the shared `closeServer` helper. All production DI and every test module and fixture SHALL bind or retain this same exact `List<McpServerComponent>` value; no second list factory, helper-internal DI lookup, holder, registry, or aggregate SHALL be used. It SHALL delete `McpServer.kt` entirely and SHALL NOT introduce a reduced project server subclass, `McpRuntime`, `ServerHolder`, or another aggregate owner. `McpServerComponent` SHALL remain the grouping boundary for registered tools. DI SHALL use local isolated Koin contexts and SHALL NOT use the global `startKoin` context.

#### Scenario: All transports resolve one SDK server
- **WHEN** stdio, SSE, Streamable HTTP, or `UpdateTools` needs an MCP server
- **THEN** it SHALL use the directly composed SDK `Server`
- **AND** no project-owned aggregate server or transport wrapper SHALL participate.

### Requirement: Inline SDK-Managed Tool Execution
Tool handlers SHALL execute inline in the SDK request-handler coroutine. The project SHALL rely on SDK 0.15.0 for request IDs, `RequestHandlerExtra`, bounded handler jobs, cancellation, response suppression, and teardown. The project SHALL preserve `runCatchingExceptCancellation` so cooperative cancellation propagates as `CancellationException` and SHALL create no detached tool job or parallel active-tool registry.

#### Scenario: Tool ownership is singular
- **WHEN** a tool request is dispatched on any supported session
- **THEN** its handler SHALL run in the SDK-owned request context
- **AND** the project SHALL not launch a detached tool coroutine or wrap transport request identity.

### Requirement: Cross-Transport Cooperative Cancellation
The server SHALL rely exclusively on SDK 0.15.0 built-in cancelled-notification behavior for tool requests on stdio-compatible, ChannelTransport, SSE, and Streamable HTTP sessions. It SHALL not register a custom cancellation handler. Cancellation SHALL be cooperative and SHALL suppress the normal response for a cancelled request.

#### Scenario: Real SSE cancellation suppresses a response
- **WHEN** `SseCancellationE2ETest` uses a real Ktor server, SDK `Client`, and `SseClientTransport`
- **AND** it cancels a request after a `CompletableDeferred` confirms the handler is active
- **THEN** the handler SHALL observe cancellation
- **AND** no normal tool result SHALL surface
- **AND** client, session, server, and components SHALL be closed deterministically.

#### Scenario: Deterministic cancellation isolation
- **WHEN** a single-session `ChannelTransport` test uses deferred gates to observe four concurrent request states
- **THEN** a quick call SHALL complete while a slow call remains in flight
- **AND** cancelling the slow call SHALL leave the completed quick call successful
- **AND** a subsequent independent quick call SHALL succeed without poisoning
- **AND** the slow handler SHALL observe cooperative cancellation.

### Requirement: Concurrent Request Dispatch
The server SHALL allow independent requests on one session to proceed concurrently according to the SDK bounded per-connection limits of 64 executing and 256 in flight.

#### Scenario: Quick work is not blocked by slow work
- **WHEN** a slow tool call suspends on one ChannelTransport session
- **AND** a quick call is dispatched on that same session
- **THEN** the quick call SHALL complete before the slow call is released
- **AND** cancelling the slow call SHALL not cancel the quick call.

### Requirement: Session-Local On-Demand Roots
Roots SHALL be resolved per-call. If `server.sessions[clientConnection.sessionId]` returns no matched session or `matchedSession.clientCapabilities?.roots == null`, it SHALL return `null`; otherwise it SHALL invoke that matched session's `listRoots()`, map an empty response to `emptySet()`, and propagate transient `listRoots()` failures as tool errors without collapsing them. `GradleProjectRootInput.resolve()` and `GradleDocsTools.resolveVersion` SHALL be suspend and delegate to pure `resolveRoot(roots: Set<Root>?)`, preserving current env fallback, implicit/single/multiple, explicit name, containment, and null-explicit semantics. Fixtures SHALL use official SDK client roots APIs and a round trip through `Client.addRoot/addRoots/removeRoot/removeRoots` without `sendRootsListChanged()`. The project SHALL not use `RootsListChangedNotification`, StateFlow/cache/state, setters, update methods, or any project roots scope.

#### Scenario: Multi-client roots isolation
- **WHEN** multiple clients are connected to the server
- **AND** each client configures different roots via official SDK client root APIs
- **THEN** a tool call from client A SHALL see only A's roots
- **AND** a tool call from client B SHALL see only B's roots
- **AND** the server SHALL not maintain any global or shared project roots state.

### Requirement: Best-Effort SDK-First Teardown
The shared `closeServer(server: Server, components: List<McpServerComponent>)` helper SHALL call `Server.close()` first to stop sessions and initiate cooperative cancellation, then sequential list-order best-effort closes of every `McpServerComponent` with per-component exception isolation. SDK close cancels but does not join handler jobs and has no internal timeout; `Server.close()` has no one-shot guard. Repeated calls are operationally safe because there are no custom `onClose` callbacks and components are repeat-safe, but it is NOT guaranteed to be exactly-once. The helper MAY implement an optional cooperative timeout, but it SHALL not claim to abandon blocking non-cooperative work. Tests SHALL await a cancellation signal using a separate timeout.

#### Scenario: SDK-first order ensures cooperative unblock
- **WHEN** an active session is closed through `closeServer`
- **THEN** it MUST prove: (1) SDK `Server.close()` is invoked first, (2) components are closed sequentially in original list order, (3) one component close failure does not prevent subsequent closes, (4) repeated cleanup is operationally safe (not an idempotence guarantee), and (5) handler cancellation is observed via a separate timeout-bound signal, proving the helper return is not proof of SDK handler join.
- **AND** SDK-owned handler jobs SHALL observe cooperative cancellation
- **AND** repeated close calls SHALL be safe.

### Requirement: Migration Verification, Rollback, and Governance
This is a normative governance requirement. The SDK version bump, complete `McpServer.kt` deletion, direct composition, roots and fixture migration, behavior tests, generated tool review, documentation synchronization, and verification SHALL be one cohesive change. All source sets SHALL compile, `:updateToolsList` SHALL be run and reviewed, `:check` SHALL pass, and strict OpenSpec validation SHALL pass. Rollback SHALL revert the integrated change. Every commit, push, and PR mutation requires explicit authorization. Renovate #233 SHALL be closed as superseded and MUST NEVER be merged itself, only after this integrated change merges to main with green verification.

#### Scenario: Integrated migration passes its gates
- **WHEN** the implementation is prepared for handoff
- **THEN** the release evidence matrix, API gates, tests, generated diff review, and rollback SHALL be recorded
- **AND** no unrelated tool metadata or stale wrapper design SHALL remain
- **AND** duplicate-registration and invalid-params `-32602` behavior SHALL remain incidental.
