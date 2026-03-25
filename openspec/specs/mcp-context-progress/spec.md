# Capability: mcp-context-progress

## Purpose

Defines the improved MCP progress reporting API with simplified sub-task propagation and conditional emission based on progress token presence.

## Requirements

### Requirement: Improved progress reporting API

The `McpContext` SHALL provide an improved API for reporting progress that simplifies use in nested service calls.

#### Scenario: Simplified progress emission

- **WHEN** a service is called from within an `McpContext` tool execution
- **THEN** the context SHALL make it easy to propagate sub-task progress back to the client

### Requirement: Conditional progress emission

Progress notifications SHALL only be emitted when a progress token is present in the request metadata.

#### Scenario: Progress token check

- **WHEN** `emitProgressNotification` is called
- **THEN** the system SHALL verify the presence of a `progressToken` before sending the notification
