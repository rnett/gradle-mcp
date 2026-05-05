# Capability: dependency-source-search

## Purpose

Defines how dependency-source search resolves scopes, ranks matches, formats snippets, and includes manifest-backed indexes such as dependency and synthetic JDK sources.
## Requirements
### Requirement: Search Result Import and Package De-prioritization

The system SHALL identify matches found within `import` and `package` statements and apply a score penalty to them. This penalty SHALL be sufficient to ensure that matches in actual code (class definitions, methods, etc.) appear above these
matches for the same search term.

#### Scenario: Search for common type like 'List'

- **WHEN** searching for "List" in dependency sources
- **THEN** matches in class bodies and declarations SHALL be ranked higher than matches in `import` or `package` statements
- **AND** import- and package-only matches SHALL appear lower in the result set

### Requirement: Clean Search Snippets

The system SHALL provide clean snippets in search results that focus on the match context. The snippet SHALL NOT contain redundant line number prefixes in the content.

#### Scenario: Display search result snippet

- **WHEN** formatting a search result for display
- **THEN** the snippet content SHALL contain only the lines of source code

### Requirement: Deterministic Project Path Resolution

The system SHALL require a specific project path (or root `:`) when resolving and searching dependency sources to ensure deterministic resolution.

#### Scenario: Searching sources with project path

- **WHEN** a user searches dependency sources providing a specific project path
- **THEN** the system SHALL resolve dependencies against that specific project's configuration
- **AND** return search results from those resolved dependencies

### Requirement: JDK CAS Index Participation

Dependency-source search SHALL include auto-included JDK source indexes through the same manifest-backed session view used for dependency source indexes.

#### Scenario: Search JVM source-set session view

- **WHEN** `search_dependency_sources` resolves a JVM-backed source-set scope
- **AND** the detected JDK provides a local `src.zip`
- **THEN** the session manifest SHALL include the synthetic JDK entry at `jdk/sources`
- **AND** search SHALL query the JDK provider index alongside dependency provider indexes
- **AND** JDK search result paths SHALL remain prefixed with `jdk/sources/`

