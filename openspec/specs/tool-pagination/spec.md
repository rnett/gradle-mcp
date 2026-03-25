# Capability: tool-pagination

## Purpose

Defines the standardized pagination parameter model and LLM-optimized metadata format used across all MCP tools returning large result sets.

## Requirements

### Requirement: Standardized Pagination Parameters

The system SHALL provide a standardized way to pass `offset` and `limit` parameters to tools that return potentially large result sets.

#### Scenario: Pagination parameters in tool input

- **WHEN** a tool is defined using the standard pagination input model
- **THEN** the tool's JSON schema MUST include optional `offset` (default 0) and `limit` (default 20) fields

### Requirement: LLM-Optimized Pagination Metadata

The system SHALL include high-signal metadata in paginated tool responses that explicitly informs an LLM how to retrieve the next set of results.

#### Scenario: Metadata with explicit next steps

- **WHEN** a tool returns a paginated result
- **THEN** the response MUST include a clear block stating the current range, total items, and the exact `offset` to use for the next page
