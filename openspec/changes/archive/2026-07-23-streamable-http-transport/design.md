## Context

The MCP server currently supports two transports:
- **Stdio**: Direct stdin/stdout transport for embedded LLM tool calls.
- **SSE**: HTTP-based transport using Ktor's embedded server with the SDK's `mcp {}` plugin block.

Both transports duplicate the same Koin `McpServer` resolution pattern (try/catch + log + rethrow). The `post-upgrade-hygiene` proposal addresses this by extracting it into a shared `Application.resolveMcpServer()` helper.

MCP SDK 0.14.0 introduces `mcpStreamableHttp()`, a Ktor plugin for Streamable HTTP transport — the recommended transport per SDK documentation.

## Goals / Non-Goals

**Goals:**
- Add `Transport.StreamableHttp` as an additive third transport option.
- Install `mcpStreamableHttp()` Ktor plugin in the embedded server, following the same patterns as the SSE path.
- Share `McpServer` resolution via `Application.resolveMcpServer()` across all transports.
- Add CLI dispatch for `streamable-http` mode.

**Non-Goals:**
- Replacing or modifying existing Stdio/SSE behavior.
- Changing the `DI.kt` module (Koin bindings are unchanged).
- Streaming session management beyond what the SDK provides out of the box.

## Decisions

### Decision 1: Use `mcpStreamableHttp()` Ktor plugin directly

The `StreamableHttp` transport follows the same structural pattern as `Sse`: create an `EmbeddedServer` via `EngineMain.createServer(args)`, apply a plugin configuration block to `server.application`, then start.

```kotlin
override suspend fun start(application: Application, wait: Boolean) {
    if (server != null) error("Already started")
    val server = EngineMain.createServer(application.args)
    this.server = server
    server.application.apply {
        mcpStreamableHttp {
            server = Application.resolveMcpServer(application)
        }
    }
    server.startSuspend(wait = wait)
}
```

**Rationale**: This mirrors the SSE pattern (`mcp { ... }`) and minimizes unfamiliarity. The `mcpStreamableHttp()` DSL is structurally equivalent to `mcp {}` from the SDK.

### Decision 2: Shared Koin resolution via `Application.resolveMcpServer()`

Extract the duplicated try/catch block from both `Stdio.start()` and `Sse.start()` into a companion function:

```kotlin
companion object {
    private val LOGGER = LoggerFactory.getLogger(Application::class.java)

    fun resolveMcpServer(application: Application): McpServer = try {
        application.koinContext.get<McpServer>()
    } catch (t: Throwable) {
        LOGGER.error("Failed to initialize MCP Server", t)
        throw t
    }
}
```

This replaces the inline resolution in Stdio, SSE, and will be used by StreamableHttp.

**Rationale**: Single source of truth for error handling semantics. Consistent logging across all transports.

### Decision 3: Dedicated `streamableHttp()` entry point in `Application.Companion`

Analogous to `stdio()` and `server()`, add:

```kotlin
@JvmStatic
fun streamableHttp(args: Array<String>) = runBlocking {
    Application(args, Transport.StreamableHttp()).start()
}
```

Dispatch in `main()` adds a check for `"streamable-http"` mode before the existing stdio/server logic.

**Rationale**: Matches the existing three-entry-point structure. Clear separation of concerns.

### Decision 4: No additional transport-specific configuration

Streamable HTTP inherits host/port from standard Ktor config via `EngineMain.createServer(args)` (same as SSE). The MCP endpoint path uses the SDK plugin default.

**Rationale**: Keeps configuration surface minimal. If users need different paths or settings, those can be added later when real usage patterns emerge.

## Risks / Trade-offs

- **SDK version lock**: Requires MCP SDK 0.14.0+. If SDK rolls back, this feature is blocked. → Low risk; SDK 0.14.0 is already a dependency target.
- **Duplicate pattern**: StreamableHttp will look nearly identical to Sse structurally. → Acceptable trade-off for clarity; they are genuinely different transport mechanisms.
- **No spec changes needed**: Existing specs only define transport availability, not implementation. Adding a transport doesn't modify any requirements — purely additive.

## Migration Plan

N/A — this is purely additive. No migration, rollback, or data changes involved.

## Open Questions

None identified at this time.