## MODIFIED Requirements

### Requirement: JDK Source Indexing

The system SHALL index extracted JDK sources lazily, materializing provider indexes under the JDK CAS entry's `index/` directory only when declaration resolution is actually required. Indexing SHALL be triggered when either:

1. a dependency-source search provider is requested for a source scope (the existing search behavior, unchanged), or
2. a read operation requires symbol or package resolution (e.g., the requested read path is not on disk and package contents are explored).

Plain file and directory reads SHALL NOT trigger indexing.

#### Scenario: Index creation for JDK sources

- **WHEN** JDK sources are extracted and cached
- **AND** a search provider is requested for that source scope
- **THEN** the system SHALL create a Lucene index under `<cacheDir>/cas/v3/<hash>/index/`
- **AND** the index SHALL be usable by the existing `IndexService` and `SearchProvider` infrastructure
- **AND** indexed file paths SHALL be prefixed with `jdk/sources/`

#### Scenario: Package exploration during reads indexes on demand

- **WHEN** JDK sources are extracted and cached
- **AND** a read operation requires package or symbol resolution (e.g., the requested read path is not on disk)
- **THEN** the system SHALL materialize the Lucene index on demand under `<cacheDir>/cas/v3/<hash>/index/`
- **AND** the index SHALL be usable by the existing `IndexService` and `SearchProvider` infrastructure
- **AND** indexed file paths SHALL be prefixed with `jdk/sources/`

#### Scenario: Plain read does not index

- **WHEN** a read operation resolves an existing file or directory path
- **THEN** the system SHALL NOT build or require a declaration index for that read

### Requirement: JdkSourceService Interface

The system SHALL provide a `JdkSourceService` interface with a `resolveSources()` method that returns a `CASDependencySourcesDir?`, and SHALL expose on-demand declaration indexing so callers materialize the declaration index only when declaration resolution is required. Requesting a search provider and invoking the on-demand indexing entry point are both explicit indexing requests; resolution without either SHALL NOT build provider indexes.

#### Scenario: Successful resolution

- **WHEN** `resolveSources()` is called with valid JDK configuration
- **THEN** the system SHALL return a `CASDependencySourcesDir` pointing to the cached JDK sources
- **AND** the system SHALL NOT build provider indexes unless declaration indexing is explicitly requested (via a search provider or the on-demand indexing entry point)

#### Scenario: JDK sources unavailable

- **WHEN** `resolveSources()` is called but no `src.zip` can be found
- **THEN** the system SHALL return `null` (not throw an exception)
- **AND** the caller SHALL silently skip JDK source inclusion

## ADDED Requirements

### Requirement: Read defers declaration indexing until symbol resolution is required

read_dependency_sources SHALL NOT request declaration indexing for plain reads in any read scope (dependency session views, JDK CAS entries, and Gradle-owned sources); the declaration index SHALL be materialized on demand only when the read needs symbol or package resolution (e.g., the requested path does not exist on disk and package contents are explored), using the indexing mechanism appropriate to the scope: dependency session views (including their JDK CAS entries) via the SourcesService on-demand entry point, and Gradle-owned sources via GradleSourceService's on-demand mechanism under gradle-sources.

#### Scenario: Plain file read leaves no index markers

- **WHEN** `read_dependency_sources` is called with an existing file path (e.g., under `jdk/sources/...`)
- **THEN** the read SHALL return the file content
- **AND** the read SHALL NOT create declaration index markers for the JDK CAS entry

#### Scenario: Package exploration triggers indexing

- **WHEN** `read_dependency_sources` is called with a path that does not exist on disk and is a package
- **THEN** the system SHALL materialize the declaration index on demand
- **AND** return the package contents with correct symbols

#### Scenario: Gradle-owned package read indexes on demand

- **WHEN** `read_dependency_sources` is called with `gradleOwnSource=true` and a path that does not exist on disk and is a package
- **AND** the Gradle-owned sources under `gradle-sources` have no declaration index yet
- **THEN** the system SHALL materialize the declaration index on demand under the Gradle-owned sources storage
- **AND** return the same package contents as an already-indexed read of that package

#### Scenario: Search behavior unchanged

- **WHEN** `search_dependency_sources` is called with a search type
- **THEN** the requested provider SHALL be indexed as before
- **AND** search results SHALL be unchanged

#### Scenario: Evicted session view remains recoverable

- **WHEN** a `CachedView` for a scope is evicted from the session-view cache
- **THEN** the CAS entries SHALL remain recoverable from the session-view manifest
