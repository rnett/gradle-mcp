# Capability: dependency-filter-matcher

## ADDED Requirements

### Requirement: Coordinate + version combined matching

The system SHALL provide a `DependencyFilterMatcher` class that accepts an optional coordinate regex (`dependency` parameter) and an optional version regex (`versionFilter` parameter). The `matches(dep)` method SHALL return `true` only when both filters pass (absent filters are treated as passing). The version filter SHALL check `latestVersion` when available, falling back to `version`.

#### Scenario: Both filters pass
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:lib(:.*)?$"` and `versionFilterRegex="^2\\."`
- **AND** a dependency has group `org.example`, name `lib`, version `1.0.0`, and latestVersion `2.1.0`
- **THEN** `matches(dep)` SHALL return `true` because both the coordinate regex matches (`org.example:lib:1.0.0`) and the version regex matches (`2.1.0` matches `^2\\.`).

#### Scenario: Coordinate filter fails
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^other:lib(:.*)?$"` and no version filter
- **AND** a dependency has group `org.example`, name `lib`, version `1.0.0`
- **THEN** `matches(dep)` SHALL return `false`.

#### Scenario: Version filter fails
- **WHEN** `DependencyFilterMatcher` is constructed with `versionFilterRegex="^3\\."`
- **AND** a dependency has latestVersion `2.1.0`
- **THEN** `matches(dep)` SHALL return `false`.

#### Scenario: No filters
- **WHEN** `DependencyFilterMatcher` is constructed with both filters as `null`
- **THEN** `matches(dep)` SHALL return `true` for any dependency.

#### Scenario: Version filter uses `latestVersion` when available
- **WHEN** `DependencyFilterMatcher` is constructed with `versionFilterRegex="^4\\."`
- **AND** a dependency has version `1.0.0` but latestVersion `4.0.0`
- **THEN** `matches(dep)` SHALL return `true` because `latestVersion` is checked first.

#### Scenario: No version for version filter
- **WHEN** `DependencyFilterMatcher` is constructed with `versionFilterRegex="^3\\."`
- **AND** a dependency has both `version` and `latestVersion` as `null`
- **THEN** `matches(dep)` SHALL return `false` because no version is available to match.

### Requirement: Post-processing in GradleDependencyService

The system SHALL apply `DependencyFilterMatcher` as a post-processing pass on the parsed `GradleDependencyReport` in `DefaultGradleDependencyService`. The post-processing SHALL recursively walk the dependency tree and prune nodes that do not match the combined filter. A node SHALL be kept if it matches or if any of its descendants match. Non-matching children of matching parents SHALL be pruned.

#### Scenario: Prune non-matching top-level dependency
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:lib(:.*)?$"`
- **AND** the parsed report has a configuration with two top-level dependencies: `org.example:lib:1.0.0` (which matches the coordinate filter) and `other:tool:2.0.0` (which does not match)
- **THEN** the post-processing SHALL remove `other:tool:2.0.0` and its children
- **AND** SHALL keep `org.example:lib:1.0.0`.
- **AND** children of `org.example:lib:1.0.0` SHALL only be retained if they independently match the filter or are structural ancestors of a matching descendant.

#### Scenario: Keep non-matching parent with matching child
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:nested-util(:.*)?$"`
- **AND** configuration has dependency `org.example:parent:1.0.0` with child `org.example:nested-util:1.0.0`
- **THEN** the post-processing SHALL keep `org.example:parent:1.0.0` (because its descendant matches) and emit `org.example:nested-util:1.0.0` as a child.

#### Scenario: Prune non-matching children of matching parent
- **WHEN** `DependencyFilterMatcher` is constructed with `dependencyFilterRegex="^org\\.example:lib(:.*)?$"`
- **AND** configuration has dependency `org.example:lib:1.0.0` with child `other:transitive:1.0.0`
- **THEN** the post-processing SHALL keep `org.example:lib:1.0.0` with no children (the non-matching transitive dependency is pruned).

#### Scenario: No filter applied
- **WHEN** `GradleDependencyService.getDependencies` is called without a `dependency` or `versionFilter`
- **THEN** the post-processing SHALL NOT be applied and the full dependency tree SHALL be returned.

### Requirement: Backward compatibility

The system SHALL maintain full backward compatibility: existing tool input schemas and output formats SHALL NOT be affected by the introduction and use of `DependencyFilterMatcher`.

#### Scenario: Tool args unchanged
- **WHEN** `inspect_dependencies` is called with the same arguments as before the change
- **THEN** the outputs SHALL be identical (the `DependencyFilterMatcher` introduction is a refactoring, not a behavior change).
