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
