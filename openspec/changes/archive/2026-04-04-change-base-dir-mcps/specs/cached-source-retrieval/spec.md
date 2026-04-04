## MODIFIED Requirements

### Requirement: Skip Gradle dependency resolution when sources are cached

The system SHALL allow skipping the expensive Gradle dependency resolution and re-download process when cached sources for a given scope (project, configuration, or source set) are already available on disk. The default cache root for this
data is `~/.mcps/rnett-gradle-mcp/cache/` (overridable via `GRADLE_MCP_WORKING_DIR`).

#### Scenario: Using cached sources when fresh is false (default)

- **WHEN** the `read_dependency_sources` or `search_dependency_sources` tool is called (using the default `fresh = false`)
- **AND** the sources and index for the specified scope already exist in the local cache
- **THEN** the system SHALL return the cached sources directory directly without executing any Gradle tasks or re-calculating dependency hashes

#### Scenario: Forcing refresh when fresh is true

- **WHEN** the `read_dependency_sources` or `search_dependency_sources` tool is called with `fresh = true`
- **THEN** the system SHALL always execute the Gradle dependency resolution and re-calculate dependency hashes, even if cached sources for the scope already exist

#### Scenario: Automatic fallback to refresh when cache is missing

- **WHEN** the `read_dependency_sources` or `search_dependency_sources` tool is called with `fresh = false`
- **AND** the sources for the specified scope do NOT exist in the local cache
- **THEN** the system SHALL execute the full Gradle dependency resolution and extraction process as if `fresh = true` was specified
