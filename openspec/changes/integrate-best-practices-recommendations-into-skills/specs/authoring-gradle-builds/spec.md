## ADDED Requirements

### Requirement: High-Impact Authoring Footgun Body Rules

The `authoring-gradle-builds` `SKILL.md` body SHALL carry the `[Writes build logic]` hardest-to-figure-out highlights and High-severity cross-cutting rules as always-loaded rules. The rules SHALL cover, where applicable, keeping expensive work out of configuration, configuration avoidance, provider and managed-property laziness, reading providers only at execution boundaries, public APIs and injected services, model relationships for cross-project behavior, avoiding `afterEvaluate` (version-sensitive), and distinguishing `set(null)` from an absent provider.

#### Scenario: Apply an authoring footgun rule

- **WHEN** an agent is about to author or modify build logic without loading a reference
- **THEN** the body exposes the applicable high-impact rule with a concise reason
- **AND** it links to the authored reference containing the detailed guidance and snippet

#### Scenario: Apply version-sensitive authoring guidance

- **WHEN** an authoring footgun is marked version-sensitive
- **THEN** the body or linked reference directs the agent to read the wrapper version first
- **AND** the agent checks the exact Gradle version before applying the rule

### Requirement: Authored Authoring Best-Practice References

The skill SHALL provide authored, non-generated references carrying the remaining `[Writes build logic]` recommendations and all do/don't snippets. Guidance SHALL be woven into existing references such as `build-lifecycle.md`, `managed-types-and-providers.md`, `custom-tasks.md`, `dependencies-and-catalogs.md`, `convention-plugins.md`, `plugin-development.md`, `jdk-toolchains.md`, and `configurations-and-variants.md`, or into new authored-local files where no natural home exists. The references SHALL preserve the frozen corpus and its `Index -> Detail -> Gradle Docs` escalation.

#### Scenario: Use the authored procedural reference

- **WHEN** an agent performs an authoring action covered by the recommendation field guide
- **THEN** it loads the relevant authored reference as the single procedural source
- **AND** it can find the recommendation, its do/don't snippet, and its `gradle_docs` hint there

#### Scenario: Follow corpus escalation without duplication

- **WHEN** the authored reference points to generated best-practice rationale
- **THEN** it links through `references/best-practices/_index.md` and the matching detail file
- **AND** it does not restate the generated corpus prose

## MODIFIED Requirements

### Requirement: Best Practices Integration

The skill MUST integrate the frozen generated best-practice corpus as rooted local references, maintaining the lookup order `Index -> Detail -> Gradle Docs`, and MUST avoid duplicating authored guidance that belongs in the generated corpus. Authored best-practice references SHALL coexist with the frozen corpus as the single procedural load for the recommendation field guide; corpus detail remains optional rationale consulted on demand, is cross-linked through `Index -> Detail -> Gradle Docs`, and SHALL never be restated as a competing procedural load.

#### Scenario: Use authored guidance with optional corpus rationale

- **WHEN** an agent is deciding between two plugin application patterns
- **THEN** it loads the relevant authored reference as the single procedural source
- **AND** it may follow `references/best-practices/_index.md` to the matching generated detail entry and query version-scoped `gradle_docs` when deeper rationale or the authoritative source is needed
- **AND** the escalation remains `Index -> Detail -> Gradle Docs` without requiring a second competing procedural load
