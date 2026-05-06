# Capability: dependency-filtering

## Purpose

Defines regex-based dependency filtering behavior shared by dependency-source tools and dependency inspection.

## Requirements

### Requirement: Coordinate regex filtering

The system SHALL support filtering dependencies by providing a `dependency` full-string Kotlin regular expression in `search_dependency_sources`, `read_dependency_sources`, and `inspect_dependencies`. Blank dependency filters SHALL be treated as absent at tool/service boundaries. The regex SHALL match canonical resolved dependency coordinates in the form `group:name:version` or `group:name:version:variant`; unresolved declared dependencies SHALL use `group:name` because no selected version or variant exists; project dependencies SHALL use `project::path` where `path` is the Gradle project path (for example `project::lib` or `project::sub:util`). Dependency filters are trusted input: the system SHALL preserve Kotlin/JVM regex semantics and SHALL NOT replace them with a reduced regex engine or reject otherwise-valid regexes for performance reasons. Complex regex patterns may be expensive, and callers are responsible for trusted/safe regexes.

#### Scenario: Filter by group:name

- **WHEN** the `dependency` parameter is set to `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core(:.*)?$`
- **THEN** the system SHALL only include sources/info from dependencies matching that group and name.

#### Scenario: Filter by group

- **WHEN** the `dependency` parameter is set to `^org\.jetbrains\.kotlinx(:.*)?$`
- **THEN** the system SHALL include sources/info from all dependencies in that group.

#### Scenario: Filter by version

- **WHEN** the `dependency` parameter is set to `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core:1\.7\.3$`
- **THEN** the system SHALL only include sources/info from that specific version.

#### Scenario: Filter by variant

- **WHEN** the `dependency` parameter is set to `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core:1\.7\.3:jvm$`
- **THEN** the system SHALL only include sources/info from that specific version and variant.

#### Scenario: No matches in populated scope

- **WHEN** the `dependency` parameter does not match any resolved dependencies in the current scope (project, configuration, or source set)
- **AND** the unfiltered scope contains dependency candidates
- **THEN** the system SHALL return an informative error message indicating that no matching dependencies were found in the specified scope.

#### Scenario: Empty scope

- **WHEN** the `dependency` parameter is supplied
- **AND** the selected scope contains no dependency candidates
- **THEN** the system SHALL return a successful empty result with a visible note that the selected scope contains no dependency sources for the filter to match.

#### Scenario: Blank filter

- **WHEN** the `dependency` parameter is blank or whitespace
- **THEN** the system SHALL treat it as absent and SHALL NOT apply dependency filtering.

#### Scenario: Matches without source artifacts

- **WHEN** the `dependency` parameter matches dependencies in the selected scope
- **AND** none of the matched dependencies has source artifacts
- **THEN** dependency source tools SHALL return a distinct diagnostic explaining that the regex matched dependencies but none have sources.

#### Scenario: Regex compilation

- **WHEN** the `dependency` parameter is provided
- **THEN** the system SHALL compile it as a regular expression before expensive source extraction or indexing.
- **AND** invalid non-blank regexes SHALL fail using Kotlin/JVM regex construction semantics.

#### Scenario: Unresolved dependency fallback

- **WHEN** an unresolved declared dependency has group `org.example` and name `missing-artifact`
- **AND** the `dependency` parameter is set to `^org\.example:missing-artifact$`
- **THEN** the system SHALL consider the unresolved dependency a match.

### Requirement: Flexible Dependency Matching (KMP Support)

The system SHALL support regex patterns that express prefix matching for artifact names to accommodate Kotlin Multiplatform (KMP) artifacts which often have platform suffixes (e.g., `-jvm`, `-js`).

#### Scenario: Prefix match for artifact name

- **WHEN** the `dependency` parameter is set to `^ai\.koog:prompt-structure.*$`
- **AND** a resolved dependency has group `ai.koog` and name `prompt-structure-jvm`
- **THEN** the system SHALL consider this a match.

#### Scenario: Prefix match with version

- **WHEN** the `dependency` parameter is set to `^ai\.koog:prompt-structure.*:0\.0\.1$`
- **AND** a resolved dependency has group `ai.koog`, name `prompt-structure-jvm`, and version `0.0.1`
- **THEN** the system SHALL consider this a match.

### Requirement: View Cache and CAS Boundary

Dependency source filtering SHALL affect the session view cache key because different filters produce different returned source views. Dependency filtering SHALL NOT affect CAS identity, CAS hashes, CAS directory names, lock paths, extraction markers, normalized paths, or source indexes.

#### Scenario: Filtered views are cached independently

- **WHEN** dependency source tools are called with the same scope and different `dependency` filters
- **THEN** each filter SHALL have a distinct session-view cache entry
- **AND** `fresh` and `forceDownload` SHALL invalidate only the exact requested view cache entry.

#### Scenario: CAS identity ignores dependency filter

- **WHEN** the same dependency is selected by different dependency filters
- **THEN** the same CAS hash and CAS directory SHALL be used for that dependency
- **AND** force-refresh for a filtered call SHALL refresh only the dependencies selected by that filtered call without creating regex-specific CAS entries.

### Requirement: Graph-Wide Matching Without Implicit Closure

The `dependency` filter SHALL match direct or transitive dependency nodes in the selected graph when `onlyDirect=false`. A matched transitive node MAY be emitted independently even if its parent does not match. Matching a dependency SHALL NOT automatically include that dependency's transitive children unless those children also match the filter.

#### Scenario: Matched dependency children are not implicitly included

- **WHEN** `search_dependency_sources` is called with `dependency="^org\\.jetbrains\\.kotlinx:kotlinx-coroutines-core(:.*)?$"`
- **THEN** the system SHALL ONLY include sources for `kotlinx-coroutines-core`
- **AND** the system SHALL NOT include sources for `kotlin-stdlib` or other dependencies of `kotlinx-coroutines-core`.

#### Scenario: Matched transitive node may appear independently

- **WHEN** `inspect_dependencies` or dependency-source tools are called with `onlyDirect=false`
- **AND** a transitive dependency matches the `dependency` regex while its parent does not
- **THEN** the matching transitive dependency SHALL be eligible for output.
