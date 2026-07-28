# Capability: dependency-filtering

## ADDED Requirements

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
