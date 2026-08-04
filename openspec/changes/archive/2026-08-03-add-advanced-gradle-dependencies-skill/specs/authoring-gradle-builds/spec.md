# Capability Deltas: authoring-gradle-builds

## ADDED Requirements

### Requirement: Advanced Dependency Depth Handoff
The skill MUST retain basic dependency authoring — dependency declarations, version-catalog basics (everyday catalog entries and library declarations), repository and content-filter wiring, constraints/BOMs, and basic dependency locking — together with the conditional-only dependency verification doctrine and its basic cautions, and MUST route advanced dependency depth to `advanced-gradle-dependencies` through a `## Cross-Skill Handoffs` row and a frontmatter negative trigger. Advanced depth comprises dependency verification implementation (`verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, and CI verification workflows), feature variants and configuration roles, capability conflict resolution, locking lock modes, advanced version catalog topics, component metadata rules, dependency substitution, composite builds, and repository governance modes.

#### Scenario: Hand off capability conflicts
- **WHEN** an agent hits a capability conflict, feature-variant selection problem, version-catalog work beyond everyday entries, or governance-mode authoring beyond basic repository declaration
- **THEN** `authoring-gradle-builds` routes it to the advanced dependency engineering handoff (`advanced-gradle-dependencies`)

#### Scenario: Hand off dependency verification implementation
- **WHEN** an agent is asked to enable or repair dependency verification, or to author verification metadata, PGP keys or checksums, or CI verification workflows
- **THEN** `authoring-gradle-builds` keeps the conditional-doctrine UX-cost warning and routes the implementation to the advanced dependency engineering handoff (`advanced-gradle-dependencies`)

#### Scenario: Keep basic dependency authoring
- **WHEN** an agent authors basic dependency declarations, everyday version-catalog entries, or basic locking
- **THEN** it stays in `authoring-gradle-builds` without activating the advanced skill

## MODIFIED Requirements

### Requirement: Dependency Verification Doctrine
The skill MUST present dependency verification as conditional guidance only, with honest reporting of the UX costs associated with its adoption. Verification implementation — `verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, and CI verification workflows — MUST route to `advanced-gradle-dependencies` through the Advanced Dependency Depth Handoff. The authoritative dependency procedure (`references/dependencies-and-catalogs.md`) MUST retain the conditional framing, the UX-cost reporting, the locking-vs-verification distinction, and the disable caution, but MUST NOT direct verification implementation inside `authoring-gradle-builds`.

#### Scenario: Implement dependency verification
- **WHEN** an agent is asked to enable dependency verification
- **THEN** it informs the user of the UX costs before applying the configuration
- **AND** it routes the verification metadata, key/checksum, repair, and CI workflow implementation to `advanced-gradle-dependencies`
