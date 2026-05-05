# Capability: cached-source-retrieval

## ADDED Requirements

### Requirement: JDK Sources Use Dependency CAS Semantics

JDK sources SHALL be stored as immutable content-addressed dependency CAS entries keyed by the full SHA-256 hash of `src.zip`.

#### Scenario: Refresh JDK source resolution

- **WHEN** `fresh` is requested for a scope that includes JDK sources
- **THEN** completed JDK CAS base entries SHALL be reused
- **AND** dependency resolution and session views SHALL be refreshed
- **AND** readers SHALL be scoped to the active read/search call rather than cached across calls

#### Scenario: Force rebuild JDK source CAS entry

- **WHEN** `forceDownload` is requested for a scope that includes JDK sources
- **THEN** the completed JDK CAS entry SHALL be cleared and rebuilt under the same base lock semantics as dependency source CAS entries
- **AND** provider indexes SHALL be rebuilt under the provider index lock

