# Capability: streamable-http-transport

## Purpose

Defines the Streamable HTTP transport option for the MCP server, served via the MCP SDK 0.14.0 `mcpStreamableHttp()` Ktor plugin. It is an additional transport offered alongside — not in place of — the existing stdio and SSE transports.

## Requirements

### Requirement: Streamable HTTP as an Additional Transport

The `Transport` sealed class in `Application.kt` SHALL include a `StreamableHttp` subclass alongside the existing `Stdio` and `Sse` subclasses. Streamable HTTP SHALL be an additive option: the stdio and SSE transports SHALL remain available and their observable behavior SHALL be unchanged.

#### Scenario: Transport selection is additive

- **WHEN** the server is started in the Streamable HTTP mode
- **THEN** it SHALL serve MCP over Streamable HTTP via the `mcpStreamableHttp()` Ktor plugin
- **AND** starting the server in `stdio` or `server` (SSE) mode SHALL behave exactly as before.

### Requirement: Ktor Plugin Integration

The `StreamableHttp` transport SHALL install the SDK's `mcpStreamableHttp()` Ktor plugin into the embedded server application, supplying the `McpServer` resolved from the Koin context. It SHALL follow the same plugin contract as the SSE transport's `mcp {}` block.

- **Shared Resolution**: The `McpServer` SHALL be resolved through the shared Koin helper (`Application.resolveMcpServer()`, see `post-upgrade-hygiene`) rather than a transport-local copy of the resolution try/catch block.

#### Scenario: Plugin receives the Koin-resolved server

- **WHEN** the Streamable HTTP server application is configured
- **THEN** the `mcpStreamableHttp()` plugin SHALL be installed with the `McpServer` obtained from `Application.resolveMcpServer()`
- **AND** a Koin resolution failure SHALL be logged ("Failed to initialize MCP Server") and rethrown, consistent with the other transports.

### Requirement: Transport Configuration

The `StreamableHttp` transport SHALL derive host and port from the same Ktor configuration mechanism used by the SSE transport (`EngineMain.createServer(args)` reading the application config), and SHALL serve the MCP endpoint at the path defined by the SDK plugin, configurable where the plugin supports it.

#### Scenario: Configuration via standard Ktor config

- **WHEN** the Streamable HTTP transport is started
- **THEN** its host and port SHALL come from the standard Ktor server configuration/args, as with SSE
- **AND** the MCP endpoint path SHALL follow the `mcpStreamableHttp()` plugin default unless explicitly configured.

### Requirement: CLI Mode Dispatch

`Application.main` SHALL recognize a Streamable HTTP mode and dispatch it to the `StreamableHttp` transport, alongside the existing `stdio` and `server` modes. A dedicated entry point analogous to `Application.server(args)` SHALL start the Streamable HTTP transport.

#### Scenario: Selecting Streamable HTTP from the CLI

- **WHEN** the process is started with the Streamable HTTP mode argument (for example `streamable-http`)
- **THEN** `Application.main` SHALL construct `Transport.StreamableHttp` and start it
- **AND** the existing `stdio`/empty and `server` dispatch behavior SHALL be unchanged.
