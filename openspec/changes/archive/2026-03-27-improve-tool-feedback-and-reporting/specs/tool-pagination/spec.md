## MODIFIED Requirements

### Requirement: LLM-Optimized Pagination Metadata

The system SHALL include high-signal metadata in paginated tool responses that explicitly informs an LLM how to retrieve the next set of results.

#### Scenario: Metadata with explicit next steps

- **WHEN** a tool returns a paginated result
- **THEN** the response MUST include a clear block stating the current range, total items, and the exact `offset` to use for the next page

#### Scenario: Metadata for log tailing

- **WHEN** a tool returns the "tail" of a result set (e.g., last 100 lines)
- **THEN** the metadata SHALL explicitly indicate that it's showing the range from the end (e.g., "last 100 lines").
- **AND** it SHALL provide follow-up instructions for tailing (e.g., "To see more previous lines, use offset=X").
