# mcp-sdk-wrapper-reduction Specification

## Purpose
TBD - created by archiving change reduce-mcp-sdk-wrappers. Update Purpose after archive.
## Requirements
### Requirement: Captured SDK Request Context
The SDK tool handler SHALL resolve the invoking `ServerSession?`, `RequestHandlerExtra?`, and progress token exactly once and SHALL pass them to `McpContext` with the project `Json` and session-local `ClientConnection`. `McpContext` SHALL NOT carry the aggregate SDK `Server` or raw `CallToolRequest`.

#### Scenario: Active request context is adopted
- **WHEN** the SDK invokes a registered project tool
- **THEN** the handler SHALL capture `server.sessions[this.sessionId]`, `currentRequestHandlerExtra()`, and `request.meta?.progressToken`
- **AND** independently rooted progress work SHALL use those captured values without another coroutine-context lookup.

### Requirement: Correlated Async Notifications
Queued notifications SHALL be delivered with `extra?.sendNotification(notification) ?: clientConnection.notification(notification)`. The captured extra SHALL provide request correlation when available, while the connection fallback SHALL preserve delivery when no extra exists.

#### Scenario: Captured extra survives the progress scope boundary
- **WHEN** the progress pipeline sends from its separately rooted asynchronous scope
- **AND** an SDK request extra was captured in the active handler
- **THEN** delivery SHALL use `RequestHandlerExtra.sendNotification`
- **AND** SHALL retain SDK request correlation.

#### Scenario: Null extra falls back to the session connection
- **WHEN** no request extra was captured
- **THEN** the notification SHALL be delivered through the invoking `ClientConnection`
- **AND** no global or cross-session route SHALL be used.

### Requirement: Session-Local Capability-Gated Roots
Roots resolution SHALL use the pre-resolved `McpContext.session`. It SHALL return null roots without calling `listRoots()` when the session is absent or the client did not advertise roots. Otherwise it SHALL query that session on demand.

#### Scenario: Client without roots capability remains graceful
- **WHEN** a client without the roots capability invokes a tool with an explicit project root
- **THEN** roots resolution SHALL not call the capability-gated SDK `listRoots()` operation
- **AND** the explicit project root SHALL resolve under the existing null-roots policy.

### Requirement: Dead Wrapper Removal
The project SHALL remove unused elicitation wrappers and schema conversion, logging wrapper, auxiliary tool content collection, and `AuxiliaryResults` without compatibility shims. Test fixtures SHALL advertise only the roots capability unless a test explicitly owns another capability.

#### Scenario: Removed wrappers have no stale callers
- **WHEN** source and tests are searched after the refactor
- **THEN** no definitions or callers of the removed wrapper symbols SHALL remain
- **AND** fixture configuration SHALL contain no vestigial elicitation capability.

### Requirement: Direct isError Result Policy
String, unit, and structured tool result conversion SHALL read `McpToolContext.isError` directly. Exception conversion and direct `CallToolResult` passthrough SHALL preserve their existing behavior.

#### Scenario: Handler marks a successful value as an MCP error
- **WHEN** a tool handler sets `isError = true` and returns a normal string, unit, or structured value
- **THEN** the converted `CallToolResult` SHALL retain that content
- **AND** its `isError` field SHALL be true.

### Requirement: Retained Project Boundaries
The project SHALL retain component grouping and close hooks, delegated tool registration, schema and argument handling, result/error policy, progress behavior, SDK-first close, application transport ownership, local isolated Koin, the exact shared component list, and the fixture client's real-dispatcher request hop. Registration flattening SHALL NOT be part of this change.

#### Scenario: Existing behavior remains covered
- **WHEN** the wrapper reduction is verified
- **THEN** progress, roots isolation, cancellation, teardown, schema generation, decoding, result conversion, and component lifecycle tests SHALL remain enabled
- **AND** no global Koin context or second component list SHALL be introduced.

#### Scenario: Fixture registration and teardown share component identity
- **GIVEN** a test module factory-binds the component list
- **WHEN** the fixture constructs and later closes its SDK server
- **THEN** it SHALL resolve the component list exactly once
- **AND** the exact component instances registered with that server SHALL receive the component close hook.
