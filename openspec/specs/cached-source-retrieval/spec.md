# Capability: cached-source-retrieval

## Purpose

Optimizes dependency source operations by caching resolved sources and skipping expensive Gradle resolution when cached data is available.

## Requirements

### Requirement: Skip Gradle dependency resolution when sources are cached

The system SHALL allow skipping the expensive Gradle dependency resolution and re-download process when cached sources for a given scope (project, configuration, or source set) are already available on disk.

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

### Requirement: Display last refresh timestamp

The output of `read_dependency_sources` and `search_dependency_sources` SHALL start with a message indicating when the sources for the requested scope were last refreshed (i.e., when the Gradle resolution last ran).

#### Scenario: Displaying timestamp in tool output

- **WHEN** the `read_dependency_sources` or `search_dependency_sources` tool returns its results
- **THEN** the output SHALL include a header with a nicely formatted local timestamp of the last refresh and the elapsed time since that refresh (e.g., "Sources last refreshed at 2026-03-03 14:00 (15 minutes ago)")
