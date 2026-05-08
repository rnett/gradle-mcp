## ADDED Requirements

### Requirement: Exploring Dependency Sources covers all source code scopes

The `exploring_dependency_sources` skill (renamed from `searching_dependency_sources`) SHALL provide guidance for searching and reading source code across ALL scopes:

- Project dependency source code (via `search_dependency_sources` and `read_dependency_sources` with project, configuration, or source set scope)
- Plugin (buildscript) source code (via `sourceSetPath=":buildscript"` or `configurationPath=":buildscript:classpath"`)
- Gradle Build Tool internal source code (via `gradleSource: true`)

#### Scenario: Agent searches project dependency source

- **WHEN** an agent needs to find a class definition in a project dependency
- **THEN** the skill provides guidance on `DECLARATION` search with `projectPath` or `dependency` filter

#### Scenario: Agent searches Gradle internal source

- **WHEN** an agent needs to understand Gradle engine behavior
- **THEN** the skill provides guidance on `gradleSource: true` searches with `DECLARATION`, `FULL_TEXT`, or `GLOB` search types

#### Scenario: Agent reads plugin source code

- **WHEN** an agent needs to examine a Gradle plugin's implementation
- **THEN** the skill provides guidance on `sourceSetPath=":buildscript"` searches and `read_dependency_sources` with plugin paths

### Requirement: Exploring Dependency Sources Constitution unchanged from predecessor

The constitution from `searching_dependency_sources` SHALL be preserved and expanded:

- Using `search_dependency_sources` as the primary discovery tool for external library, plugin, and Gradle internal code
- Preferring source reading over REPL exploration for API understanding
- Providing absolute paths for `projectRoot`
- Using `{group}/{artifact}` prefix syntax for reading specific files
- Escaping Lucene special characters in `FULL_TEXT` searches
- Using `fresh: true` when project dependencies change
- Targeting buildscript dependencies with `sourceSetPath=":buildscript"`

#### Scenario: Agent discovers an unfamiliar API

- **WHEN** an agent encounters a library class without documentation
- **THEN** the skill directs the agent to `DECLARATION` search before attempting runtime exploration

#### Scenario: Agent needs to read Gradle internals

- **WHEN** an agent asks "how does Gradle resolve dependencies internally?"
- **THEN** the skill directs to `gradleSource: true` searches for the relevant engine classes

### Requirement: Search mode guidance covers all scopes

The skill SHALL document the three search modes (`DECLARATION`, `FULL_TEXT`, `GLOB`) and their applicability across dependency, plugin, and Gradle internal scopes:

- `DECLARATION`: case-sensitive symbol search by name or FQN with `name:` and `fqn:` field prefixes, glob wildcards, and regex support
- `FULL_TEXT`: case-insensitive text search with Lucene query syntax
- `GLOB`: case-insensitive file name search

#### Scenario: Agent searches for internal APIs

- **WHEN** an agent needs to find internal Gradle classes
- **THEN** the skill provides `fqn:/.*\.internal\..*/` regex pattern on `gradleSource: true`

#### Scenario: Agent uses targeted dependency filter

- **WHEN** an agent knows the specific library to search
- **THEN** the skill shows `dependency="org:artifact"` parameter for fast scoped searches
