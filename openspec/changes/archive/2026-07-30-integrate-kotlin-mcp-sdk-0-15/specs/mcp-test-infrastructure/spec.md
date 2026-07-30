## MODIFIED Requirements

### Requirement: No Reflection Into SDK Internals
Test fixtures SHALL not use reflection to read or mutate private fields of MCP SDK classes or a project server. Roots behavior SHALL be controlled only through the official Kotlin MCP SDK client roots APIs, and tests SHALL observe the resulting root-dependent behavior through tool results. The project SHALL not provide or retain `setRootsForTesting`, a server-root setter, or another test-only mutation seam.

#### Scenario: Official client roots API replaces reflection
- **WHEN** a test needs the server to behave as if the client configured roots
- **THEN** the fixture SHALL call the SDK client roots API on the connected client
- **AND** it SHALL await the actual tool response confirming root-dependent behavior
- **AND** it SHALL not call `getDeclaredField`, set `isAccessible`, or use any reflection API against the SDK or project server.

### Requirement: Deterministic Server and Fixture Teardown
The directly composed SDK `Server`, its MCP components, and each test fixture SHALL have explicit teardown ordering. The shared `closeServer(server: Server, components: List<McpServerComponent>)` helper SHALL be suspending: it SHALL call `Server.close()` first to initiate cooperative cancellation of SDK-owned handler jobs, then close components in sequential list-order with per-component exception isolation. SDK cancellation initiation does not join handler jobs, and `closeServer` SHALL not claim to join them. Fixture-owned scopes SHALL be cancelled and joined separately, and tests SHALL observe handler cancellation through a separate timeout-bound signal; helper return SHALL not be treated as proof of SDK handler join. Lifecycle blocks performing real I/O SHALL use a generous explicit real-time `runTest` timeout.

#### Scenario: SDK cancellation and fixture scope teardown remain distinct
- **WHEN** fixture close returns after active work or a real Ktor session
- **THEN** the SDK server SHALL close before components and initiate SDK handler cancellation
- **AND** fixture-owned scopes SHALL be cancelled and joined separately
- **AND** a separate timeout-bound signal SHALL observe handler cancellation
- **AND** helper return SHALL not imply that SDK handler jobs joined
- **AND** repeated cleanup SHALL be operationally safe
- **AND** no `runBlocking` bridge or fire-and-forget cleanup SHALL be used.
