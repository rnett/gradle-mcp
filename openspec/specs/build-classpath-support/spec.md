# Capability: build-classpath-support

## Purpose

Extends dependency inspection, search, and reading tools to include buildscript (plugin) classpath dependencies alongside standard project dependencies.

## Requirements

### Requirement: Include Build Classpath in Dependency Inspection

The `inspect_dependencies` tool SHALL report all dependencies from `buildscript` configurations, prefixed with `buildscript:`, AND expose them collectively under a virtual `buildscript` source set.

#### Scenario: Inspecting the virtual buildscript source set

- **WHEN** `inspect_dependencies` is called for a project without filtering
- **THEN** the system SHALL return a report including standard configurations and `buildscript:` configurations (e.g., `buildscript:classpath`).

#### Scenario: Inspecting the virtual buildscript source set

- **WHEN** `inspect_dependencies` is called with `sourceSet = "buildscript"`
- **THEN** the system SHALL return dependencies exclusively from the buildscript configurations.

### Requirement: Search Build Classpath Sources

The `search_dependency_sources` tool SHALL NOT include the sources of buildscript dependencies in its search index by default. They MUST be accessed via the virtual `buildscript` source set.

#### Scenario: Searching for a symbol in build classpath (disabled by default)

- **WHEN** `search_dependency_sources` is called without a specific `sourceSetPath` or `configurationPath`
- **THEN** the system SHALL NOT return results from the build classpath sources.

#### Scenario: Searching for a symbol in build classpath (explicitly enabled)

- **WHEN** `search_dependency_sources` is called with `sourceSetPath` set to `:buildscript` (or similar project-scoped path)
- **THEN** the system SHALL return matching results from the build classpath sources.

### Requirement: Read Build Classpath Sources

The `read_dependency_sources` tool SHALL allow reading source files from buildscript dependencies only when the `buildscript` virtual source set or a specific `buildscript:` configuration is provided.

#### Scenario: Reading a source file from build classpath (disabled by default)

- **WHEN** `read_dependency_sources` is called with a path within a buildscript dependency without specifying the `buildscript` source set
- **THEN** the system SHALL return an error or indicate that the path was not found.

#### Scenario: Reading a source file from build classpath (explicitly enabled)

- **WHEN** `read_dependency_sources` is called with `sourceSetPath` set to `:buildscript`
- **THEN** the system SHALL return the content of the source file.
