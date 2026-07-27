## ADDED Requirements

### Requirement: SDK-Provided In-Memory Transport

The test fixtures SHALL create linked in-memory client/server transports using the MCP SDK's `kotlin-sdk-testing` module (`io.modelcontextprotocol:kotlin-sdk-testing`, `ChannelTransport.createLinkedPair()`), and the project SHALL NOT maintain a custom `AbstractTransport` implementation for in-memory testing.

#### Scenario: Fixture uses the SDK transport pair

- **WHEN** `McpServerFixture.start()` connects the server and the client
- **THEN** it SHALL connect the server to one transport of a `ChannelTransport.createLinkedPair()` and the client to the other
- **AND** no project-defined `AbstractTransport` subclass SHALL exist in the codebase.

#### Scenario: SDK transport lifecycle and buffering

- **WHEN** the fixture relies on the SDK in-memory transport
- **THEN** it SHALL NOT pass a `CoroutineScope` to the transport, because the SDK transport manages its own coroutine lifecycle
- **AND** tests SHALL NOT assume unbounded buffering: the SDK transport uses `Dispatchers.Default` with a bounded (256-element) channel rather than `Channel.UNLIMITED`.

### Requirement: No Reflection Into SDK Internals

Test fixtures SHALL NOT use reflection to read or mutate private fields of MCP SDK classes or of `McpServer`. Any server state that tests need to control SHALL be exposed through a dedicated test-only method on the project's own `McpServer` class, clearly marked as test-only.

#### Scenario: Setting roots without reflection

- **WHEN** a test needs the server to behave as if the client configured roots
- **THEN** the fixture SHALL call `McpServer.setRootsForTesting(roots)`
- **AND** the fixture SHALL NOT call `getDeclaredField`, set `isAccessible`, or use any other reflection API against the SDK or `McpServer`.

#### Scenario: Test roots are observationally identical to real roots

- **WHEN** `setRootsForTesting(roots)` is called
- **THEN** the public `McpServer.roots` `StateFlow` SHALL emit the supplied set
- **AND** root-dependent tool behavior SHALL be indistinguishable from a real `notifications/roots/list_changed` round-trip.

### Requirement: Deterministic Server and Fixture Teardown

The MCP server and its test fixture SHALL shut down deterministically so that no orphaned coroutine work bleeds across the separate `runTest` blocks (setup / test / cleanup) that JUnit runs for each test.

- **Suspending shutdown**: `McpServer` SHALL expose a suspending, idempotent `shutdown()` that closes the SDK sessions, closes the components, and then cancels its tool-execution `scope` and joins it for up to a bounded grace period (`SHUTDOWN_GRACE_MS`); if the scope does not drain in time, `shutdown()` SHALL log a warning and abandon the stuck, non-cooperatively-cancellable work rather than wait indefinitely.
- **Synchronous SDK callback**: The SDK invokes `Server.onClose` synchronously, so that callback SHALL perform only cheap, non-blocking state cleanup (clearing the active-tool-call map and cancelling the scope). It SHALL NOT bridge to suspending work via `runBlocking`; the suspending cleanup lives in `shutdown()`, which callers await.
- **Fixture teardown**: `McpServerFixture.close()` SHALL await `McpServer.shutdown()` and SHALL cancel AND join its own scope (`scope.cancel(...); scope.coroutineContext[Job]?.join()`), never fire-and-forget.
- **Idempotent component close**: `McpServerComponent.close()` implementations SHALL be safe to call more than once, because the fixture may also close shared managers (e.g., `ReplManager`) directly.
- **Generous real-time budget**: Because fixture lifecycle blocks perform real I/O (Gradle builds, REPL workers) under virtual-time `runTest`, the fixture setup/cleanup `runTest` blocks SHALL use an explicit generous real-time timeout (e.g., `runTest(timeout = 2.minutes)`) so that slow CI dispatch does not fail deterministic teardown.

#### Scenario: Cleanup fully drains before the next test

- **WHEN** `McpServerFixture.close()` returns
- **THEN** the server's tool-execution scope and the fixture scope SHALL have no active coroutines
- **AND** the SDK `onClose` callback SHALL NOT have blocked on a `runBlocking` bridge.
