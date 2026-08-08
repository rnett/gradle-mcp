# Capability: dependency-filtering

## Purpose

Define the opt-in reverse-consumer and provenance semantics added to dependency report post-processing and output.

## ADDED Requirements

### Requirement: Optional direct consumer edges

`inspect_dependencies` SHALL accept `includeConsumers: Boolean = false`. When `includeConsumers=true`, the invocation SHALL behave as if `onlyDirect=false`, regardless of the default `onlyDirect=true`, so the full resolved graph is parsed and rendered and consumer inversion is complete. If the caller explicitly passes both `includeConsumers=true` and `onlyDirect=true`, `includeConsumers` SHALL win and the response SHALL include the note `"onlyDirect overridden to false for consumers inversion"`. Each dependency node SHALL then expose `consumers` as a list of lightweight `ConsumerEdge` records containing `id`, `group`, `name`, `version`, `variant: String?` (null when there is no variant), `fromConfiguration`, and `path`. The list SHALL contain direct parents only. `ConsumerEdge.path` SHALL identify the direct parent edge, using that parent's identity/path rather than a root-to-parent traversal path. Each entry SHALL represent one edge to one distinct direct parent, so diamond A -> B -> C and A -> D -> C yields two edges for C, one for B and one for D, rather than one edge per root path.

#### Scenario: Direct consumer is returned

- **GIVEN** application A depends on library B and library B depends on library C
- **WHEN** library C is inspected with `includeConsumers=true`
- **THEN** library C's `consumers` list contains library B
- **AND** the list does not add application A as a transitive consumer

#### Scenario: Transitive target is available under the default direct-only setting

- **GIVEN** application A depends on library B and library B depends on transitive library C
- **WHEN** library C is inspected with `includeConsumers=true` under the default `onlyDirect=true`
- **THEN** library C's `consumers` list contains its direct parent library B
- **AND** the full resolved graph is used for inversion

#### Scenario: Consumer inversion is disabled by default

- **WHEN** `inspect_dependencies` is called without `includeConsumers` or with `includeConsumers=false`
- **THEN** each node's `consumers` field is absent regardless of `onlyDirect`
- **AND** the tool does not perform reverse-graph inversion or incur its processing cost

### Requirement: Consumer inversion is bounded per invocation

When `includeConsumers=true`, the tool SHALL compute reverse edges in a single inversion pass and SHALL memoize the resulting consumer mapping for that invocation. The computation MUST terminate for diamond and cycle graph shapes.

#### Scenario: Diamond and cycle shapes terminate

- **GIVEN** a resolved dependency graph contains repeated diamond paths or a cycle
- **WHEN** `inspect_dependencies` runs with `includeConsumers=true`
- **THEN** reverse-edge computation terminates
- **AND** each dependency reports only its deduplicated direct parents

### Requirement: Consumer edges are deduplicated by parent identity and configuration

`consumers` SHALL deduplicate parent edges by `(commonComponentId ?: syntheticId(group, name, version, variant)) + fromConfiguration + variant`, where `syntheticId` folds the parent's own GAV, variant, and id. This identity SHALL preserve distinct direct parents when `commonComponentId` is absent and SHALL distinguish two direct parents that differ only by variant.

#### Scenario: Repeated paths do not duplicate a parent

- **GIVEN** the same parent component and source configuration reach a dependency through multiple graph paths
- **WHEN** `inspect_dependencies` runs with `includeConsumers=true`
- **THEN** the dependency contains one consumer edge for that parent identity and `fromConfiguration`
- **AND** consumer edges for other distinct direct parents remain present

#### Scenario: Distinct parents without common component identities remain separate

- **GIVEN** two distinct ordinary parents in the same source configuration both lack `commonComponentId`
- **WHEN** `inspect_dependencies` runs with `includeConsumers=true`
- **THEN** the dependency contains one consumer edge for each parent's own coordinate identity
- **AND** the two direct parents are not collapsed into one consumer edge

#### Scenario: Variant-distinct direct parents remain separate

- **GIVEN** two direct parents have the same component coordinates and source configuration but differ by `variant`
- **WHEN** `inspect_dependencies` runs with `includeConsumers=true`
- **THEN** the dependency contains one consumer edge for each parent variant
- **AND** the two entries are distinguishable through `edge.variant`

### Requirement: Dependency provenance remains available

The dependency report SHALL expose the selected version (`version`, sourced from `selected.version`), `reason`, `latestVersion`, `isDirect`, and `fromConfiguration`. Agents SHALL use the selected version together with `reason` to explain version selection. `latestVersion` SHALL remain advisory update-check data that may be absent or differ from the selected version, and SHALL NOT be used to explain why a version was selected.

#### Scenario: Conflict resolution explains the selected version

- **GIVEN** library X version 2.0 is selected over version 1.0
- **WHEN** library X is inspected
- **THEN** the selected `version` reflects version 2.0
- **AND** `reason` equals `conflict resolution`
- **AND** `latestVersion` is treated only as advisory update-check data and is not used to explain the selection
- **AND** `isDirect` and `fromConfiguration` remain available to describe how the dependency entered the graph
