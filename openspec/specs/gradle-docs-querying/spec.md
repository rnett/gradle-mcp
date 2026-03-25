# Capability: gradle-docs-querying

## Purpose

Specifies how documentation tools use resolved concrete version strings for caching, indexing, and retrieval, and how best-practices content is searchable.

## Requirements

### Requirement: Resolved versions for documentation

The documentation querying tools SHALL use concrete version strings resolved from aliases for all caching, indexing, and content retrieval operations.

#### Scenario: Query documentation with "current"

- **WHEN** the user queries documentation for version `"current"`
- **THEN** the system SHALL resolve `"current"` to a concrete version (e.g., `"8.6.1"`) and use that version for the documentation cache directory (e.g., `.../8.6.1/`)

### Requirement: Transparent version feedback

The system SHALL inform the user when a version alias has been resolved to a concrete version in its tool output.

#### Scenario: Display resolved version in tool output

- **WHEN** the documentation tool resolves `"current"` to `"8.6.1"`
- **THEN** the output header SHALL indicate that Gradle `"8.6.1"` is being used (resolved from `"current"`)

### Requirement: Search by Best Practices Tag

The documentation search tool SHALL allow users to filter for specifically tagged "best-practices" documentation.

#### Scenario: Searching for best practices

- **WHEN** user provides `query="tag:best-practices performance"` to the documentation search tool
- **THEN** only documentation tagged with `best-practices` and containing "performance" SHOULD be returned.

### Requirement: Section Summary Integration

The documentation section summary SHALL explicitly list the `best-practices` tag to aid in discoverability.

#### Scenario: Summarizing documentation sections

- **WHEN** the documentation tool is called with no arguments
- **THEN** the returned summary MUST include a "Best Practices" section with its corresponding count.
