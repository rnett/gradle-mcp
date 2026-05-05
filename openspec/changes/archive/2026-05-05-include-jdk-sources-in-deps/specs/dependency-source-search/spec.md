# Capability: dependency-source-search

## ADDED Requirements

### Requirement: JDK CAS Index Participation

Dependency-source search SHALL include auto-included JDK source indexes through the same manifest-backed session view used for dependency source indexes.

#### Scenario: Search JVM source-set session view

- **WHEN** `search_dependency_sources` resolves a JVM-backed source-set scope
- **AND** the detected JDK provides a local `src.zip`
- **THEN** the session manifest SHALL include the synthetic JDK entry at `jdk/sources`
- **AND** search SHALL query the JDK provider index alongside dependency provider indexes
- **AND** JDK search result paths SHALL remain prefixed with `jdk/sources/`

