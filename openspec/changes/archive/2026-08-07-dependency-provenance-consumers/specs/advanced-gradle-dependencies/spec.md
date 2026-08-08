# Capability: advanced-gradle-dependencies

## Purpose

Define how the advanced dependency skill routes agent questions about version provenance, direct consumers, and blast radius.

## ADDED Requirements

### Requirement: Route version-provenance questions to authoritative evidence

The skill SHALL direct agents answering "why this version?" questions to use `dependencyInsight` together with the selected `version` (sourced from `selected.version`), `reason`, `isDirect`, and `fromConfiguration` from dependency inspection results. The skill SHALL describe `latestVersion` only as an advisory signal that a newer version may be available when the field is present, and SHALL NOT use it to explain why the selected version won.

#### Scenario: Agent explains conflict resolution

- **GIVEN** library X version 2.0 is selected over version 1.0
- **WHEN** an agent asks why version 2.0 was selected
- **THEN** the skill directs the agent to `dependencyInsight` and the dependency provenance fields
- **AND** the explanation uses the selected `version` 2.0 and `reason` equal to `conflict resolution`
- **AND** `latestVersion` is treated only as an optional advisory "newer available" signal

### Requirement: Route direct-consumer questions to reverse edges

The skill SHALL direct agents answering "who depends on X?" questions to make a single `inspect_dependencies { filter=X, includeConsumers:true }` call and read the target dependency's `consumers` list. The agent SHALL NOT need to pass `onlyDirect=false`, because `includeConsumers=true` implies full-graph processing as if `onlyDirect=false`.

#### Scenario: Agent identifies a direct consumer

- **GIVEN** application A depends on library B and library B depends on library C
- **WHEN** an agent asks who depends on library C
- **THEN** the skill directs the agent to make one `inspect_dependencies { filter=C, includeConsumers:true }` call
- **AND** the call does not require the agent to pass `onlyDirect=false`
- **AND** the agent reads library B from library C's direct `consumers` edges

### Requirement: Route blast-radius questions to client-side traversal

The skill SHALL direct agents answering blast-radius questions to filter the target dependency and traverse direct `consumers` edges client-side. It MUST NOT describe or require a server-side transitive-closure API.

#### Scenario: Agent computes transitive blast radius

- **GIVEN** a dependency report contains direct `consumers` edges
- **WHEN** an agent asks for the target dependency's blast radius
- **THEN** the skill directs the agent to traverse successive direct consumer edges client-side
- **AND** the skill does not route the question to a standalone tool or transitive-closure API
