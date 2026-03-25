# Capability: build-classpath-support

## Purpose

Extends dependency inspection, search, and reading tools to include buildscript (plugin) classpath dependencies alongside standard project dependencies.

## Requirements

### Requirement: Include Build Classpath in Dependency Inspection

The `inspect_dependencies` tool SHALL report all dependencies from `buildscript` configurations, prefixed with `buildscript:`.

#### Scenario: Inspecting all project and buildscript dependencies

- **WHEN** `inspect_dependencies` is called for a project
- **THEN** the system SHALL return a report including standard configurations and `buildscript:` configurations (e.g., `buildscript:classpath`).

### Requirement: Search Build Classpath Sources

The `search_dependency_sources` tool SHALL include the sources of buildscript dependencies in its search index.

#### Scenario: Searching for a symbol in build classpath

- **WHEN** `search_dependency_sources` is called with a query matching a buildscript dependency symbol
- **THEN** the system SHALL return matching results from the build classpath sources.

### Requirement: Read Build Classpath Sources

The `read_dependency_sources` tool SHALL allow reading source files from buildscript dependencies.

#### Scenario: Reading a source file from build classpath

- **WHEN** `read_dependency_sources` is called with a path within a buildscript dependency (identified via `search_dependency_sources`)
- **THEN** the system SHALL return the content of the source file.
