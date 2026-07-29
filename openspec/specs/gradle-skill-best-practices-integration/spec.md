# Capability: gradle-skill-best-practices-integration

## Purpose

Defines how the generated best-practices reference integrates into the existing `gradle` skill's `SKILL.md` and how agents should use it in conjunction with the `gradle_docs` tool.

## Requirements

### Requirement: Generated directory replaces static snapshot

The `gradle` skill's `SKILL.md` SHALL reference the generated `references/best-practices/_index.md` as the entry point for best-practices guidance. The existing handwritten static snapshot SHALL be removed/replaced by the generated content in the `best-practices/` directory.

#### Scenario: Skill references updated

- **WHEN** an agent accesses the `gradle` skill
- **THEN** the SKILL.md Resources entry SHALL point to `references/best-practices/_index.md` as the authoritative offline best-practices reference, generated from Gradle {version} official documentation
- **AND** the static disclaimer that agents "MUST use the `gradle_docs` tool" SHALL be removed or superseded.

### Requirement: Lookup order documented

The `gradle` skill SHALL document the lookup order for build-quality guidance:
1. Read `references/best-practices/_index.md` first (entry point with categorized area grouping, summaries, and tags).
2. Pick the relevant practice by area (source page) or tag, then open the linked detail file.
3. If the question is not fully answered by the generated reference, use `gradle_docs tag:best-practices` for the most up-to-date version-specific answer.

#### Scenario: Agent guidance

- **WHEN** an agent needs to answer a build-quality or best-practices question
- **THEN** the SKILL.md SHALL guide the agent to read `_index.md` first, pick by area/tag, then open the detail file
- **AND** SHALL document the fallback to `gradle_docs tag:best-practices` for deeper or version-specific queries.

### Requirement: Generated reference freshness awareness

The `gradle` skill SHALL include a disclaimer that the generated reference is a snapshot from a specific Gradle version and may be partially outdated if the documentation has been updated in a newer version.

#### Scenario: Staleness awareness

- **WHEN** an agent loads the `gradle` skill
- **THEN** the SKILL.md SHALL note that the generated best-practices reference corresponds to a specific Gradle version
- **AND** SHALL direct agents to use `gradle_docs` for authoritative, version-appropriate guidance when the generated reference may be insufficient.
