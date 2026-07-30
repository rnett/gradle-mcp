## RENAMED Requirements
- `Shared Koin McpServer Resolution` -> `Shared Koin Server Resolution`

## MODIFIED Requirements

### Requirement: Shared Koin Server Resolution
The duplicated transport-specific Koin resolution of the MCP server SHALL be replaced with one shared `Application.resolveServer(): Server` helper returning the Kotlin MCP SDK `Server`. Stdio, SSE, and Streamable HTTP SHALL use this helper. The helper SHALL log `"Failed to initialize MCP Server"` and rethrow on resolution failure, preserving current failure behavior. The implementation SHALL resolve the directly composed SDK server and SHALL not resolve a project-owned `McpServer` aggregate.

#### Scenario: Single SDK server resolution path
- **WHEN** any application transport needs the MCP server
- **THEN** it SHALL call `Application.resolveServer()`
- **AND** no transport SHALL contain its own Koin resolution try/catch
- **AND** the returned type SHALL be the SDK `Server`.

### Requirement: Documented Coroutine and Session Design
Non-obvious MCP server design decisions SHALL have adjacent comments that describe direct SDK `Server` composition, SDK-owned bounded tool-handler jobs, the on-demand per-session roots resolution, and SDK-first close through the shared lifecycle helper. Comments SHALL not describe a project wrapper, custom cancellation handler, detached tool scope, active-tool registry, server roots setter, or `onConnect` root registration. The `toKotlinxSerialization()` enum special case SHALL remain documented because schema-kenerator emits `enum` without `type`.

#### Scenario: Lifecycle rationale is documented without stale design
- **WHEN** a developer reads the direct server construction, on-demand root resolution, or `closeServer` declaration
- **THEN** adjacent comments SHALL explain the SDK ownership and SDK-first close rationale
- **AND** no removed workaround terminology SHALL remain in the documented design.
