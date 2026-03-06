## ADDED Requirements

### Requirement: Paginated Dependency Reports

The `inspect_dependencies` tool SHALL support optional `offset` and `limit` parameters to handle projects with large dependency trees.

#### Scenario: Listing dependencies with pagination

- **WHEN** `inspect_dependencies` is called with `offset=10` and `limit=10`
- **THEN** the system SHALL return only the requested range of projects or top-level dependencies in the report

### Requirement: Paginated Dependency Updates

The `inspect_dependencies` tool with `updatesOnly=true` SHALL support `offset` and `limit` to handle numerous dependency updates.

#### Scenario: Listing updates with pagination

- **WHEN** `inspect_dependencies` with `updatesOnly=true` is called with `limit=5`
- **THEN** the system SHALL return at most 5 dependency updates in the summary
