# jdk-source-resolution Specification

## Purpose

Defines how dependency-source tools detect JVM-backed Gradle scopes, resolve the configured JDK, locate local `src.zip`, and expose JDK standard library sources through the dependency-source CAS and session-view model.

## Requirements

### Requirement: JDK Home Detection via Init Script

The system SHALL detect the JDK home directory from the Gradle project's configuration via the init script when a project has JVM-backed source metadata or buildscript classpath dependencies. The init script SHALL emit a `JDK` line type
with the project's configured JDK home and version.

The detection priority SHALL be:

1. Project's configured Java toolchain (via `JavaPluginExtension.toolchain` + `JavaToolchainService`)
2. Project's configured Kotlin toolchain (via reflected Kotlin JVM toolchain metadata)
3. Gradle daemon's JDK (via `Jvm.current().javaHome`)

#### Scenario: Java toolchain configured

- **WHEN** the project has the Java plugin applied
- **AND** a Java toolchain is configured via `java { toolchain { ... } }`
- **THEN** the init script SHALL resolve the toolchain's JDK home using `JavaToolchainService`
- **AND** emit `JDK | projectPath | jdkHome | version`

#### Scenario: Kotlin toolchain configured (no Java toolchain)

- **WHEN** the project has the Kotlin plugin applied but no configured Java toolchain
- **AND** a Kotlin JVM toolchain exposes a language version
- **THEN** the init script SHALL resolve the matching JDK home with `JavaToolchainService`
- **AND** emit `JDK | projectPath | jdkHome | version`

#### Scenario: No toolchain configured

- **WHEN** no Java or Kotlin toolchain is configured
- **THEN** the init script SHALL fall back to the Gradle daemon's JDK
- **AND** emit `JDK | projectPath | jdkHome | version` using `Jvm.current().javaHome`

#### Scenario: Toolchain resolution fails

- **WHEN** toolchain resolution fails (e.g., JDK not provisioned yet)
- **THEN** the init script SHALL fall back to the Gradle daemon's JDK
- **AND** emit `JDK | projectPath | jdkHome | version` using `Jvm.current().javaHome`
- **AND** the build SHALL NOT fail

### Requirement: JDK Data in Dependency Report

The system SHALL parse the `JDK` line type from the init script output and include JDK information in the dependency report data model. The system SHALL parse source-set JVM eligibility from
`SOURCESET | projectPath | name | configurations | isJvm`. Missing `isJvm` fields SHALL default to `false`, except the buildscript source set SHALL default to `true`. Duplicate Java/Kotlin source-set names SHALL be merged, and a JVM flag
from any emitted source-set record SHALL mark the merged source set JVM-backed.

#### Scenario: JDK line parsed

- **WHEN** the init script emits `JDK | projectPath | jdkHome | version`
- **THEN** the system SHALL parse `jdkHome` and `version` from the line
- **AND** store them in `GradleProjectDependencies` as `jdkHome: String?` and `jdkVersion: String?`

#### Scenario: No JDK line emitted

- **WHEN** the init script does not emit a `JDK` line for a project
- **THEN** `jdkHome` and `jdkVersion` SHALL be `null` in the project's `GradleProjectDependencies`
- **AND** JDK source auto-inclusion SHALL be skipped for that project

#### Scenario: Source set JVM flag parsed

- **WHEN** the init script emits a `SOURCESET` line with `isJvm=true`
- **THEN** the corresponding source-set model SHALL be marked JVM-backed
- **AND** JDK source auto-inclusion SHALL be allowed for that source set

#### Scenario: Source set is not JVM-backed

- **WHEN** the init script emits a `SOURCESET` line with `isJvm=false`
- **THEN** JDK source auto-inclusion SHALL be skipped for that source set

#### Scenario: Duplicate source-set metadata emitted

- **WHEN** Java and Kotlin metadata produce the same source-set name
- **THEN** the parser SHALL merge their configuration lists
- **AND** the merged source set SHALL be JVM-backed if any matching record has `isJvm=true`

### Requirement: src.zip Location

The system SHALL locate the JDK source archive (`src.zip`) by checking the following paths in order:

1. `<javaHome>/lib/src.zip`
2. `<javaHome>/src.zip` (for older JDK layouts)

#### Scenario: src.zip found at standard location

- **WHEN** the JDK home is resolved
- **AND** `<javaHome>/lib/src.zip` exists
- **THEN** the system SHALL use that file as the JDK source archive

#### Scenario: src.zip found at legacy location

- **WHEN** the JDK home is resolved
- **AND** `<javaHome>/lib/src.zip` does not exist
- **AND** `<javaHome>/src.zip` exists
- **THEN** the system SHALL use `<javaHome>/src.zip` as the JDK source archive

#### Scenario: src.zip not found

- **WHEN** the JDK home is resolved
- **AND** neither `<javaHome>/lib/src.zip` nor `<javaHome>/src.zip` exists
- **THEN** the system SHALL log a warning indicating that JDK sources are not available
- **AND** the system SHALL silently skip JDK source inclusion (not fail the request)

#### Scenario: JDK home is invalid

- **WHEN** the detected JDK home is relative, missing, or not a directory
- **THEN** the system SHALL skip JDK source inclusion
- **AND** warn without including the full local path in warn-level diagnostics

#### Scenario: src.zip escapes JDK home

- **WHEN** a discovered `src.zip` resolves outside the accepted JDK home
- **THEN** the system SHALL reject that archive
- **AND** continue checking only the specified `src.zip` locations under the JDK home

### Requirement: JDK Source Identification (Content-Addressed)

The system SHALL identify JDK source archives by their content, using a SHA-256 hash of the `src.zip` file as the primary cache key. The JDK version detected by the init script SHALL remain in the dependency report for human readability.

#### Scenario: src.zip hash computed as primary cache key

- **WHEN** a `src.zip` file is located
- **THEN** the system SHALL compute a SHA-256 hash of the `src.zip` file content
- **AND** use the full SHA-256 hash as the primary cache key for extraction and indexing
- **AND** use the init-script JDK version only as descriptive report data, not as the cache key

#### Scenario: JDK version is unavailable

- **WHEN** the init script cannot determine a JDK version
- **THEN** the system SHALL still use the SHA-256 hash of `src.zip` as the cache key
- **AND** store a null or empty version string in the dependency report

### Requirement: JDK Source Extraction and Caching

The system SHALL extract the JDK `src.zip` into a cache directory at `<cacheDir>/cas/v3/<hash>/` using the existing `ArchiveExtractor`, where `<hash>` is the full SHA-256 hash of the `src.zip` file content.

#### Scenario: First-time extraction

- **WHEN** the cache directory for the JDK source hash does not exist or is incomplete
- **THEN** the system SHALL extract `src.zip` into `<cacheDir>/cas/v3/<hash>/sources/`
- **AND** create a completion marker at `<cacheDir>/cas/v3/<hash>/.base-completed`
- **AND** the extraction SHALL use `skipSingleFirstDir = false` (JDK `src.zip` has no single top-level directory)

#### Scenario: Cached sources already exist

- **WHEN** the cache directory exists and the completion marker is present
- **THEN** the system SHALL skip extraction and use the cached sources directly

#### Scenario: Fresh refresh requested

- **WHEN** a `fresh` parameter is set
- **THEN** the system SHALL reuse a completed CAS base entry
- **AND** refresh dependency resolution and session views

#### Scenario: Force refresh requested

- **WHEN** a `forceDownload` parameter is set
- **THEN** the system SHALL clear and rebuild the completed CAS base entry under the CAS base lock
- **AND** rebuild requested provider indexes

### Requirement: JDK Source Indexing

The system SHALL index extracted JDK sources lazily when a dependency-source search provider is requested, producing provider indexes under the JDK CAS entry's `index/` directory.

#### Scenario: Index creation for JDK sources

- **WHEN** JDK sources are extracted and cached
- **AND** a search provider is requested for that source scope
- **THEN** the system SHALL create a Lucene index under `<cacheDir>/cas/v3/<hash>/index/`
- **AND** the index SHALL be usable by the existing `IndexService` and `SearchProvider` infrastructure
- **AND** indexed file paths SHALL be prefixed with `jdk/sources/`

### Requirement: Auto-Inclusion of JDK Sources for JVM Source Sets

The system SHALL automatically include JDK sources when resolving dependency sources for JVM-backed scopes. No new JDK-specific tool parameter is required. The existing dependency filter SHALL accept `jdk` to select only the synthetic JDK
source entry.

#### Scenario: read_dependency_sources with JVM source set

- **WHEN** `read_dependency_sources` is called with a JVM source set path (e.g., `:app:main`)
- **THEN** the session view SHALL include JDK sources under `jdk/sources/...`
- **AND** the tool SHALL return JDK source content when the requested path uses that prefix and exists

#### Scenario: search_dependency_sources with JVM source set

- **WHEN** `search_dependency_sources` is called with a JVM source set path (e.g., `:app:main`)
- **THEN** the system SHALL search both the dependency source index and the JDK source index
- **AND** merge results from both indexes

#### Scenario: read or search with JVM project scope

- **WHEN** a dependency-source tool is called with `projectPath`
- **AND** any source set in that project is JVM-backed
- **THEN** the session view SHALL include JDK sources under `jdk/sources/...`

#### Scenario: read or search with JVM configuration scope

- **WHEN** a dependency-source tool is called with `configurationPath`
- **AND** the configuration belongs to a JVM-backed source set or is a buildscript configuration
- **THEN** the session view SHALL include JDK sources under `jdk/sources/...`

#### Scenario: mixed JVM and non-JVM project scope

- **WHEN** a project contains both JVM-backed and non-JVM source sets
- **THEN** project-scoped dependency-source resolution SHALL include JDK sources
- **AND** exact non-JVM source-set or configuration scopes SHALL NOT include JDK sources

#### Scenario: JDK-only dependency filter

- **WHEN** a dependency-source tool is called with `dependency = "jdk"`
- **THEN** ordinary Gradle dependencies SHALL be excluded from the dependency filter passed to Gradle
- **AND** the resulting session view SHALL include only the synthetic JDK source entry when JDK sources are available

#### Scenario: gradleOwnSource=true takes precedence

- **WHEN** `gradleOwnSource=true` is passed
- **THEN** the system SHALL resolve only Gradle sources (no JDK source auto-inclusion)

#### Scenario: Non-JVM source set

- **WHEN** the source set is not a JVM source set (e.g., metadata-only configurations)
- **THEN** the system SHALL NOT auto-include JDK sources

#### Scenario: JDK sources unavailable

- **WHEN** `src.zip` is not found for the project's configured JDK
- **THEN** the system SHALL silently skip JDK source inclusion
- **AND** the dependency source tools SHALL still work for dependency sources

### Requirement: JdkSourceService Interface

The system SHALL provide a `JdkSourceService` interface with a `resolveSources()` method that returns a `CASDependencySourcesDir?`.

#### Scenario: Successful resolution

- **WHEN** `resolveSources()` is called with valid JDK configuration
- **THEN** the system SHALL return a `CASDependencySourcesDir` pointing to the cached JDK sources
- **AND** the system SHALL populate provider indexes only when a `SearchProvider` is requested

#### Scenario: JDK sources unavailable

- **WHEN** `resolveSources()` is called but no `src.zip` can be found
- **THEN** the system SHALL return `null` (not throw an exception)
- **AND** the caller SHALL silently skip JDK source inclusion

