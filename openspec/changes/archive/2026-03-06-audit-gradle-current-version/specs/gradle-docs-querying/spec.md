## ADDED Requirements

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
