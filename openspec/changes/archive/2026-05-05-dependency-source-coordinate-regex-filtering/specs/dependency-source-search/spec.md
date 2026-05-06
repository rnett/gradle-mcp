## ADDED Requirements

### Requirement: Regex Coordinate Filtering

The system SHALL support filtering dependency sources and `inspect_dependencies` by applying a full-string Kotlin regular expression to canonical dependency coordinates. Blank dependency filters SHALL be treated as absent at tool/service boundaries. Resolved dependencies SHALL expose `group:name:version` and, when variant data is available, `group:name:version:variant` candidates. Unresolved dependencies SHALL expose `group:name` candidates only. Project dependencies SHALL expose `project::path` candidates, where `path` is the Gradle project path. Dependency filters are trusted input and SHALL preserve Kotlin/JVM regex semantics; complex patterns may be expensive and remain the caller's responsibility.

#### Scenario: Match a coordinate family

- **WHEN** the filter regex matches coordinates such as `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3`
- **THEN** the system SHALL include only dependencies whose coordinates match the regex

#### Scenario: Match by artifact and version shape

- **WHEN** the filter regex is `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core(:.*)?$`
- **THEN** the system SHALL match the dependency regardless of whether the service layer appends variant information

#### Scenario: Match exact version

- **WHEN** the filter regex is `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core:1\.7\.3$`
- **THEN** the system SHALL match dependencies with that exact selected version

#### Scenario: Match exact variant

- **WHEN** the filter regex is `^org\.jetbrains\.kotlinx:kotlinx-coroutines-core:1\.7\.3:jvm$`
- **THEN** the system SHALL match dependencies with that exact selected version and variant

### Requirement: Consistent Regex Evaluation

The system SHALL apply the same regex filtering semantics in the Gradle-side pruning path and the service-side verification path.

#### Scenario: Gradle and service layers agree

- **WHEN** the Gradle prefilter retains a dependency candidate
- **THEN** the service layer SHALL use the same regex semantics to confirm the final result set

### Requirement: Invalid Regex Rejection

The system SHALL reject an invalid dependency filter regex before performing expensive dependency extraction or indexing.

#### Scenario: Invalid regex input

- **WHEN** a user provides an invalid regex such as `*[`
- **THEN** the system SHALL return a clear error message explaining that the filter could not be parsed as a regular expression

### Requirement: No-Match Feedback

The system SHALL return an informative error when the regex matches no dependencies in a non-empty scope.

#### Scenario: Regex matches nothing in a populated scope

- **WHEN** the dependency scope contains candidates but none of them match the regex
- **THEN** the system SHALL report that no matching dependencies were found in the specified scope

#### Scenario: Regex has no candidates in an empty scope

- **WHEN** the dependency scope contains no candidates
- **AND** a dependency filter was supplied
- **THEN** the system SHALL return a successful empty result with a visible note explaining that the selected scope contains no dependency sources

#### Scenario: Blank filter is ignored

- **WHEN** the dependency filter is blank or whitespace
- **THEN** the system SHALL behave as if the filter were omitted

#### Scenario: Regex matches dependencies without sources

- **WHEN** the dependency scope contains dependencies matching the regex
- **AND** the matched dependencies do not have source artifacts
- **THEN** dependency-source tools SHALL report that the regex matched dependencies but none have sources

### Requirement: View Cache and CAS Boundary

The system SHALL include the dependency regex in the session-view cache key and SHALL exclude the dependency regex from CAS identity, hashes, directory names, lock paths, extraction markers, normalized paths, and source indexes. Reusable in-memory session-view cache entries SHALL use Caffeine, SHALL be bounded to 128 keys, SHALL expire after 30 minutes of idle time, and SHALL use bounded session-view locks rather than an unbounded per-filter lock map.

#### Scenario: Filtered source views are distinct

- **WHEN** the same scope is requested with two different dependency filters
- **THEN** the system SHALL cache and return distinct session views for those filters

#### Scenario: CAS identity is filter-independent

- **WHEN** the same dependency is selected by different dependency filters
- **THEN** the dependency SHALL use the same CAS hash and CAS directory

#### Scenario: Filtered source matching is graph-wide without implicit child closure

- **WHEN** dependency-source tools run with a dependency regex
- **AND** a transitive dependency matches the regex while its parent does not
- **THEN** the matching transitive dependency SHALL still be eligible for output
- **AND** matching a dependency SHALL NOT automatically include that dependency's children unless they also match the regex

### Requirement: Inspect Update Filtering

The system SHALL apply the dependency regex to `inspect_dependencies` report rendering and update-check candidate selection. When `onlyDirect=false`, matched transitive nodes MAY appear independently even if their parent node does not match, and matching a dependency SHALL NOT automatically include that dependency's children unless they also match the regex.

#### Scenario: Filtered update candidates

- **WHEN** `inspect_dependencies` runs with `checkUpdates=true` and a dependency regex
- **THEN** update checks SHALL only be attempted for dependencies that match the regex and requested directness scope
