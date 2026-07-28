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


### Requirement: Server-side post-processing contract

The system SHALL apply `DependencyFilterMatcher` as a server-side post-processing pass on the parsed `GradleDependencyReport` in `DefaultGradleDependencyService`. The pass SHALL run after the init script output is parsed and before the report is returned. The architecture SHALL consist of two layers:

1. **Init-script authoritative filtering**: The init script performs the primary filter during Gradle dependency resolution, update-candidate selection, and source-candidate selection, using its own private copies of coordinate-formatting functions.
2. **Server-side consistency pass**: `DefaultGradleDependencyService` applies `DependencyFilterMatcher` on the parsed report to ensure behavioral consistency regardless of init script version.

The post-processing SHALL recursively walk the dependency tree and prune nodes that do not match the combined filter. A node SHALL be kept if it matches the filter or if any of its descendants match. Non-matching children of matching parents SHALL NOT be implicitly retained (consistent with the Graph-Wide Matching Without Implicit Closure requirement). `GradleDependencyTools` SHALL NOT perform independent filtering — it consumes the already-filtered report from the service.

#### Scenario: Prune non-matching top-level dependency
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:lib(:.*)?$"`
- **AND** the parsed report has a configuration with `org.example:lib:1.0.0` (matches) and `other:tool:2.0.0` (no match)
- **THEN** the post-processing SHALL remove `other:tool:2.0.0` and its children
- **AND** SHALL keep `org.example:lib:1.0.0`.
- **AND** children of `org.example:lib:1.0.0` SHALL only be retained if they independently match the filter or are structural ancestors of a matching descendant.

#### Scenario: Keep non-matching parent with matching child
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:nested-util(:.*)?$"`
- **AND** configuration has dependency `org.example:parent:1.0.0` with child `org.example:nested-util:1.0.0`
- **THEN** the post-processing SHALL keep `org.example:parent:1.0.0` (because its descendant matches) and emit `org.example:nested-util:1.0.0` as a child.

#### Scenario: No filter applied
- **WHEN** `GradleDependencyService.getDependencies` is called without a `dependency` or `versionFilter`
- **THEN** the post-processing SHALL NOT be applied and the full dependency tree SHALL be returned.

#### Scenario: Tools layer does not independently filter
- **WHEN** `GradleDependencyTools` receives a filtered report from the service
- **THEN** it SHALL NOT apply an additional filtering pass
- **AND** it SHALL render the report as received.
