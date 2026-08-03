## ADDED Requirements

### Requirement: Canonical documentation-link representation

Every documentation link in AUTHORED skill content — a `SKILL.md` body or an authored reference — SHALL be a prose `gradle_docs` tool-call hint: `gradle_docs(path="<clean .md path>")` for a known page or `gradle_docs(query="tag:<tag> <term>")` for a search entry point. The known-page form is the general clean path `gradle_docs(path="<clean .md path>")`: a valid page path may carry the `userguide/`, `dsl/`, `kotlin-dsl/`, `javadoc/`, or `samples/` prefix, or be the root `release-notes.md`, with `gradle_docs(path="userguide/<page>.md")` as the common illustrative example, not the only allowed shape. A path SHALL contain no `#fragment` or `?query`, and `version` SHALL be omitted unless the target differs from the wrapper. A stored search link SHALL be scoped with `tag:<tag>` so the link is precise and self-documenting. This requirement governs authored stored links and future generator output only; it does not constrain operational `gradle_docs` searches an agent performs at runtime, where the documentation lookup ladder MAY intentionally broaden a search by dropping the tag. Path reads and the no-argument section browse take no tag. The frozen generated best-practices corpus is confirm-only and is explicitly OUT OF SCOPE for this requirement's "every link is clean" rule: its links are not held to the clean-path rule here, and the single known malformed generated javadoc member-anchor hint is a grandfathered frozen exception recorded, not fixed, not regenerated, and not hand-edited.

#### Scenario: Cite a known page

- **WHEN** an authored reference points a reader to a specific known documentation page
- **THEN** it carries `gradle_docs(path="userguide/<page>.md")`, an example of the general clean-path form `gradle_docs(path="<clean .md path>")`, with no fragment, query, or version

#### Scenario: Cite a search entry point

- **WHEN** a topic is best entered by search
- **THEN** the link is `gradle_docs(query="tag:<tag> <term>")`

#### Scenario: Pin a different version

- **WHEN** the research target differs from the wrapper
- **THEN** the link adds `version="X.Y"` and a one-line migration or verification note
- **AND** when the target does not differ from the wrapper, `version` is omitted

#### Scenario: Tag scoping is mode-correct

- **WHEN** an author writes a search link into a skill
- **THEN** the stored link is tag-scoped as `gradle_docs(query="tag:<tag> <term>")`, while path reads and no-argument calls take no tag
- **AND** this replaces the false every-call-must-be-tagged rule without constraining runtime lookup-ladder searches, whose broaden step may drop the tag

#### Scenario: Generated frozen corpus is confirm-only

- **WHEN** this change confirms the frozen generated best-practices corpus
- **THEN** the corpus is not subject to this requirement's "every link is clean" rule and is not regenerated or hand-edited
- **AND** the single known malformed generated javadoc member-anchor hint is recorded as a grandfathered frozen exception rather than fixed, regenerated, or hand-edited

### Requirement: Documentation-link coverage convention

Every guidance topic in an authored reference for which a relevant Gradle documentation topic exists SHALL carry at least one canonical `gradle_docs` link to a relevant Gradle documentation page, as a "find out more / see for details" pointer; a topic for which no relevant Gradle documentation topic exists SHALL NOT carry a manufactured link. A guidance topic is the smallest coherent topic or area of guidance a reader would want to explore further in the official documentation — practically a section, subsection, paragraph, bullet, or table row carrying a distinct topic — and is never an entire multi-topic section standing behind one unrelated link. The trigger for requiring a link is topical relevance: whether a relevant Gradle documentation topic exists for the guidance, not whether the local prose originated from the documentation. Where guidance did originate from a documentation page, that page is by definition a relevant read-more link, so documentation provenance is a natural subset of this rule rather than its predicate. A guidance topic that genuinely spans distinct Gradle documentation areas SHALL carry one canonical link per relevant area. Local cross-references and non-documentation external URLs are not guidance topics and are excluded from coverage counting. Coverage SHALL be established and maintained by human review at authoring time, not by an automated build gate.

#### Scenario: Reviewer validates coverage

- **WHEN** a reviewer inspects an authored reference
- **THEN** each guidance topic has at least one relevant canonical link, with one link per distinct relevant documentation area
- **AND** no fabricated tool names or blocked documentation URL hosts appear

#### Scenario: Identify the guidance topic

- **WHEN** a reviewer decides whether a passage needs a link
- **THEN** they treat the smallest coherent topic or area of guidance — a section, subsection, paragraph, bullet, or table row — as the unit, not an entire multi-topic section behind one unrelated link
- **AND** the test is whether a relevant Gradle documentation topic exists for the guidance, not whether the prose was documentation-derived

#### Scenario: Provenance is a subset

- **WHEN** a guidance topic originated from a specific documentation page
- **THEN** that page is a relevant read-more link and satisfies the topical requirement for the topic
- **AND** topics whose prose is original still require a link whenever a relevant documentation topic exists

#### Scenario: Topic spanning multiple areas

- **WHEN** a single guidance topic genuinely spans distinct Gradle documentation areas
- **THEN** it carries one canonical link per relevant area

#### Scenario: Runtime skills carry no manufactured citations

- **WHEN** a skill area has no genuinely relevant Gradle-documentation topic, such as the REPL or Compose runtime skills
- **THEN** no link is added and the "no link warranted" outcome is recorded

### Requirement: Per-file link structure

An authored reference SHALL place canonical links inline at points of need, where the specific topic is discussed. A trailing `More info` (or `See also`) authoritative-docs block that serves as the reference's topical "find out more" index is OPTIONAL. The trailing block, when present, aggregates the relevant documentation pages for the reference's topics; an inline link repeated in the trailing block is permitted aggregation, not duplication, because the block is an index rather than a second assertion. Two identical pointers repeated at the same inline point of need, with no aggregating trailing block, are disallowed duplication. A guidance topic that spans distinct documentation areas SHALL carry one canonical link per relevant area, each placed at the point of need for the sub-topic it addresses. Local cross-references and non-documentation external URLs SHALL NOT be converted to `gradle_docs` links and are excluded from coverage counting.

#### Scenario: Inline plus optional trailing index

- **WHEN** a reference covers topics with relevant documentation
- **THEN** topics carry inline call-form links at points of need
- **AND** the reference MAY collect those same canonical links in a trailing authoritative-docs `More info` index block without that collection counting as duplication, the trailing block being optional

#### Scenario: A topic spanning multiple pages

- **WHEN** a single guidance topic spans two distinct documentation pages
- **THEN** it carries two canonical links, each tied to the sub-topic it addresses

#### Scenario: Non-doc URLs preserved

- **WHEN** a reference cites a Maven Central Portal guide, an author metadata URL, or a license or example URL
- **THEN** it is retained as-is, not rewritten as a `gradle_docs` link, and excluded from coverage counting

### Requirement: Generated content is confirm-only and outside the canonical-link requirement

The generated best-practices corpus is confirm-only and is OUT OF SCOPE for the authored-content canonical-link requirement above. `GenerateBestPracticesDoc` is the reference implementation and already emits per-topic canonical read links for new output; the corpus SHALL NOT be regenerated or hand-edited by this change, and any future generator change SHALL preserve the per-topic canonical emission. The single known malformed generated javadoc member-anchor hint is a disclosed grandfathered frozen exception: it is recorded for reviewer awareness, not fixed, not regenerated, and not hand-edited. This note imposes no regeneration obligation.

#### Scenario: Preserve the frozen corpus

- **WHEN** this change confirms generated best-practices content
- **THEN** the existing corpus remains byte-identical
- **AND** no generator or generated file is changed

#### Scenario: Disclose the known frozen exception

- **WHEN** a reviewer confirms the frozen generated corpus
- **THEN** the single known malformed generated javadoc member-anchor hint is recorded as a grandfathered frozen exception outside the canonical-link requirement
- **AND** confirmation passes without editing, regenerating, or hand-editing the corpus
