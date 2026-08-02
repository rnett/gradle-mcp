## ADDED Requirements

### Requirement: Frozen generated corpus preserved

New best-practice guidance SHALL be added alongside the frozen generated corpus in `authoring-gradle-builds/references/best-practices/*.md` and SHALL NOT modify, regenerate, or re-hash any frozen file. New authored content SHALL carry `class: authored-local` and SHALL NOT reside inside the regenerated `best-practices/` directory.

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

New authored references SHALL carry a `class: authored-local` provenance header, SHALL be reachable from `SKILL.md` through relative links, and SHALL cite documentation through `gradle_docs` hints rather than `docs.gradle.org` or `gradle-mcp.rnett.dev` URLs. Version-sensitive entries SHALL retain a `(version-sensitive)` marker and SHALL require reading the wrapper version before application.

#### Scenario: Authored reference is reachable

- **WHEN** a new authored-local reference is added
- **THEN** it has the required provenance header and a relative link from `SKILL.md`
- **AND** `checkReferenceReachability` reports no dead reference link

#### Scenario: Version-sensitive guidance is checked

- **WHEN** an agent applies a version-sensitive recommendation
- **THEN** the reference tells it to read `gradle/wrapper/gradle-wrapper.properties` first
- **AND** the `gradle_docs` hint is resolved for the applicable wrapper or explicitly researched version

#### Scenario: Blocked documentation URLs are absent

- **WHEN** materialized skill verification scans authored references
- **THEN** no `docs.gradle.org` or `gradle-mcp.rnett.dev` documentation URL is present
- **AND** the references route through `gradle_docs` hints

## MODIFIED Requirements

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
