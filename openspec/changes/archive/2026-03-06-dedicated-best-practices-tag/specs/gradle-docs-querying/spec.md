## ADDED Requirements

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
