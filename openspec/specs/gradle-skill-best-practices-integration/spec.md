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
1. Read the always-loaded body rules for directly applicable footguns and highlights.
2. Read the authored best-practice reference for the procedural guidance and recommendation field guide.
3. Read `references/best-practices/_index.md` as the entry point for the frozen generated corpus.
4. Pick the relevant generated practice by area or tag, then open the linked detail file.
5. If the question is not fully answered, use `gradle_docs tag:best-practices` for the authoritative version-specific answer.

The generated `_index.md` SHALL remain the entry point for the frozen corpus.

#### Scenario: Resolve a footgun from the body

- **WHEN** an agent needs to answer a build-quality question covered by an always-loaded footgun rule
- **THEN** the agent resolves it directly from the body rule
- **AND** it follows the linked authored reference for procedural detail when needed

#### Scenario: Escalate beyond authored guidance

- **WHEN** an authored reference does not fully answer a build-quality question
- **THEN** the agent reads `references/best-practices/_index.md`, then the linked generated detail file
- **AND** it escalates to `gradle_docs tag:best-practices` for the authoritative version-specific answer

### Requirement: Generated reference freshness awareness

The `gradle` skill SHALL include a disclaimer that the generated reference is a snapshot from a specific Gradle version and may be partially outdated if the documentation has been updated in a newer version.

#### Scenario: Staleness awareness

- **WHEN** an agent loads the `gradle` skill
- **THEN** the SKILL.md SHALL note that the generated best-practices reference corresponds to a specific Gradle version
- **AND** SHALL direct agents to use `gradle_docs` for authoritative, version-appropriate guidance when the generated reference may be insufficient.

### Requirement: Frozen generated corpus preserved

New best-practice guidance SHALL be added alongside the frozen generated corpus in `authoring-gradle-builds/references/best-practices/*.md` and SHALL NOT modify, regenerate, or re-hash any frozen file. New authored content SHALL NOT reside inside the regenerated `best-practices/` directory and SHALL NOT contain HTML comments or provenance metadata headers.

#### Scenario: Corpus remains byte-identical

- **WHEN** authored best-practice guidance is added
- **THEN** every frozen corpus file remains byte-identical and `checkGeneratedContent` continues to validate every frozen hash
- **AND** no frozen bytes change

#### Scenario: Corpus hand-edit is rejected

- **WHEN** a contributor hand-edits a generated corpus file
- **THEN** regeneration or generated-content verification rejects or overwrites the hand-edit

### Requirement: Body-versus-reference placement

The skill bodies SHALL carry the 13 hardest-to-figure-out highlights and High-severity cross-cutting rules as always-loaded rules. All other recommendations and every do/don't snippet SHALL reside in authored references. All 134 recommendations SHALL be placed.

#### Scenario: Body rule links to its snippet

- **WHEN** a High-severity cross-cutting recommendation is selected as a body rule
- **THEN** the body carries the compact rule, one-line rationale, and a link to its authored reference
- **AND** the recommendation's do/don't snippet resides in that reference

#### Scenario: Snippets stay in references

- **WHEN** an entry has a do/don't snippet
- **THEN** the snippet is placed in an authored reference
- **AND** the snippet is not copied into the always-loaded skill body

### Requirement: Recommendation traceability

Every recommendation in `reports/gradle-best-practices-recommendations.md` SHALL be traceable to exactly one body rule or reference location, keyed by its entry title and audience tag. `[Runs builds]` entries SHALL map to `using-gradle`, and `[Writes build logic]` entries SHALL map to `authoring-gradle-builds`.

#### Scenario: Body entry is traceable

- **WHEN** an entry qualifies for body placement
- **THEN** its title and audience identify exactly one body rule location
- **AND** any detailed snippet is linked from that rule's reference

#### Scenario: Coverage is complete

- **WHEN** the implementation traceability audit runs
- **THEN** all 134 source entries have exactly one mapped location
- **AND** no entry is orphaned or mapped to an unrelated audience skill

### Requirement: Authored best-practice reference materialization

New authored references SHALL be reachable from `SKILL.md` through relative links, SHALL NOT contain HTML comments or provenance metadata headers, and SHALL cite documentation through `gradle_docs` hints rather than `docs.gradle.org` or `gradle-mcp.rnett.dev` URLs. Version-sensitive entries SHALL retain a `(version-sensitive)` marker and SHALL require reading the wrapper version before application.

#### Scenario: Authored reference is reachable

- **WHEN** a new authored reference is added
- **THEN** it has a relative link from `SKILL.md` and contains no HTML comments or provenance metadata header
- **AND** `checkReferenceReachability` reports no dead reference link

#### Scenario: Version-sensitive guidance is checked

- **WHEN** an agent applies a version-sensitive recommendation
- **THEN** the reference tells it to read `gradle/wrapper/gradle-wrapper.properties` first
- **AND** the `gradle_docs` hint is resolved for the applicable wrapper or explicitly researched version

#### Scenario: Blocked documentation URLs are absent

- **WHEN** materialized skill verification scans authored references
- **THEN** no `docs.gradle.org` or `gradle-mcp.rnett.dev` documentation URL is present
- **AND** the references route through `gradle_docs` hints

### Requirement: Authored doctrine precedence over frozen corpus examples
The skill MUST ensure that authored guidance and procedural recipes within the skills take absolute precedence over examples or patterns found in the frozen generated best-practice corpus. When a conflict exists between authored guidance (representing the modern ground-truth doctrine) and the frozen corpus, the authored guidance SHALL be treated as the authoritative source, and the frozen corpus entry SHALL be treated as optional historical rationale only.

#### Scenario: Conflict between authored and frozen guidance
- **WHEN** an agent finds a pattern in the frozen `best-practices/` corpus that contradicts a rule in an authored reference or skill body
- **THEN** it applies the authored rule and ignores the frozen example
- **AND** it optionally cites the authored rule as the reason for deviating from the corpus example

