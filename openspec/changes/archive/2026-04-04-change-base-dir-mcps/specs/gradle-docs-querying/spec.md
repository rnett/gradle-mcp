## MODIFIED Requirements

### Requirement: Resolved versions for documentation

The documentation querying tools SHALL use concrete version strings resolved from aliases for all caching, indexing, and content retrieval operations. The default cache root is `~/.mcps/rnett-gradle-mcp/cache/reading_gradle_docs` (
overridable via `GRADLE_MCP_WORKING_DIR`).

#### Scenario: Query documentation with "current"

- **WHEN** the user queries documentation for version `"current"`
- **THEN** the system SHALL resolve `"current"` to a concrete version (e.g., `"8.6.1"`) and use that version for the documentation cache directory (e.g., `…/reading_gradle_docs/8.6.1/`)
