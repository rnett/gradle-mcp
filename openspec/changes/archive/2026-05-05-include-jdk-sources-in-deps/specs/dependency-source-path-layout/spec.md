# Capability: dependency-source-path-layout

## ADDED Requirements

### Requirement: Reserved JDK Session-View Prefix

The dependency-source session view SHALL reserve the full `jdk/sources` subtree for the synthetic JDK source entry.

#### Scenario: Synthetic JDK entry

- **WHEN** JDK sources are included in a dependency-source session view
- **THEN** the manifest entry SHALL use `relativePath = "jdk/sources"`
- **AND** callers SHALL address files explicitly, for example `jdk/sources/java.base/java/lang/String.java`

#### Scenario: Dependency attempts reserved prefix

- **WHEN** an ordinary dependency prefix normalizes to `jdk/sources` or any child path under `jdk/sources/`
- **THEN** session-view creation SHALL reject that prefix before creating links or manifest entries

