# Capability: mcp-sdk-wrapper-reduction

## MODIFIED Requirements

### Requirement: Captured SDK Request Context
The SDK adapter SHALL capture `RequestHandlerExtra?` exactly once, decode `CallToolRequest` arguments into typed input, invoke a typed handler with `ProgressReporter`, and convert `ToolCallResult<O>` using a serializer captured at registration time. `McpContext` SHALL retain only schema conversion and the shared tool logger helpers; it SHALL NOT carry aggregate SDK request, session, roots, or per-call JSON state.

#### Scenario: Active request context is adopted
- **WHEN** the SDK invokes a registered project tool
- **THEN** the adapter SHALL capture request metadata before suspension
- **AND** decode, invoke, convert, and close the per-call pipeline in the defined `try`/`finally` lifecycle

### Requirement: Correlated Async Notifications
Queued notifications SHALL use a bounded `DROP_OLDEST` pipeline and be delivered in enqueue order with `extra?.sendNotification(notification) ?: clientConnection.notification(notification)`. Normal dispatched collectors and `trySend` SHALL preserve request correlation, serialized transport, bounded backpressure, and cancellation isolation.

#### Scenario: Captured extra survives the progress scope boundary
- **WHEN** the pipeline sends from its separately rooted asynchronous scope
- **AND** an SDK request extra was captured in the active handler
- **THEN** delivery SHALL use `RequestHandlerExtra.sendNotification` and retain SDK request correlation

#### Scenario: Null extra falls back to the session connection
- **WHEN** no request extra was captured
- **THEN** the notification SHALL be delivered only through the invoking `ClientConnection`

### Requirement: Session-Local Capability-Gated Roots
Production and fixture code SHALL no longer depend on MCP roots, session lookup, or capability-gated root listing. `GradleProjectRootInput` SHALL resolve a nonblank explicit path before a nonblank `GRADLE_MCP_PROJECT_ROOT` value, expanding, resolving, and normalizing the selected path, and SHALL throw a clear `IllegalArgumentException` when neither exists.

#### Scenario: Client without roots capability remains graceful
- **WHEN** a client invokes a tool with an explicit project root
- **THEN** the explicit project root SHALL resolve without calling roots APIs
- **AND** no roots or session query SHALL participate in resolution

### Requirement: Dead Wrapper Removal
The project SHALL remove unused elicitation wrappers, schema conversion wrappers, logging wrappers, auxiliary tool content collection, `AuxiliaryResults`, `McpContext`, `McpToolContext`, and roots helpers without compatibility shims. `McpContext.kt` SHALL retain only unrelated schema and logger helpers. Fixtures SHALL advertise only capabilities required by each test.

#### Scenario: Removed wrappers have no stale callers
- **WHEN** source and tests are searched after the refactor
- **THEN** no definitions or callers of removed wrapper symbols SHALL remain
- **AND** fixture configuration SHALL contain no removed capability or roots plumbing

### Requirement: Direct isError Result Policy
String, unit, null, structured, and direct `CallToolResult` conversion SHALL preserve the handler-carried `isError` state. Direct SDK results SHALL OR their own error state with the handler state. Conversion SHALL not perform runtime `Any?` guessing or per-call serializer lookup.

#### Scenario: Handler marks a successful value as an MCP error
- **WHEN** a tool handler returns a normal string, unit, null, structured value, or direct `CallToolResult` with `isError = true`
- **THEN** the converted `CallToolResult` SHALL retain its content
- **AND** its `isError` field SHALL be true

### Requirement: Retained Project Boundaries
The project SHALL retain component grouping and lifecycle, tool registration, schema and argument handling, result and exception policy, progress behavior, SDK-first close, application transport ownership, local isolated Koin, the exact shared component list, the fixture client's real-dispatcher request hop, and `GradleConnectionService`.

#### Scenario: Existing behavior remains covered
- **WHEN** wrapper reduction is verified
- **THEN** progress, cancellation, teardown, schema generation, decoding, result conversion, component lifecycle, and Gradle connection tests SHALL remain enabled
- **AND** no global Koin context, compatibility shim, registration flattening, or replacement roots abstraction SHALL be introduced

#### Scenario: Fixture registration and teardown share component identity
- **GIVEN** a test module factory-binds the component list
- **WHEN** the fixture constructs and later closes its SDK server
- **THEN** the exact component instances registered with that server SHALL receive the component close hook
