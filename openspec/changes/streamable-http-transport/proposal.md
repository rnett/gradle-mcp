## Why

MCP SDK 0.14.0 adds `mcpStreamableHttp()`, a Ktor plugin for Streamable HTTP transport — the recommended transport for new MCP deployments per SDK documentation. Streamable HTTP offers better compatibility with proxies, load balancers, and serverless environments compared to SSE.

## What Changes

- Add a `Transport.StreamableHttp` subclass to the `Transport` sealed class alongside existing `Stdio` and `Sse` subclasses.
- Install the SDK's `mcpStreamableHttp()` Ktor plugin in the embedded server application, receiving the `McpServer` via a shared Koin resolution helper.
- Add CLI mode dispatch (`streamable-http`) to `Application.main` and a dedicated entry point analogous to `Application.server(args)`.
- Configuration via standard Ktor mechanism (port, path).
- Introduce shared Koin resolution helper (`Application.resolveMcpServer()`) to eliminate duplicated try/catch Koin resolution blocks across transports.

## Capabilities

### New Capabilities
- `streamable-http-transport`: Streamable HTTP transport option using the MCP SDK 0.14.0 `mcpStreamableHttp()` Ktor plugin.

### Modified Capabilities
- None — all existing requirements for stdio and SSE transports remain unchanged.

## Impact

- `src/main/kotlin/dev/rnett/gradle/mcp/Application.kt` — Transport sealed class, main() dispatch, server configuration, Koin resolution logic.
- Existing `Transport.Stdio` and `Transport.Sse` paths — share a common resolution helper.
- No changes to transport contracts, behavior, or APIs of existing transports.