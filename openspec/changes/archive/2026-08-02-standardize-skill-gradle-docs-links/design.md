## Context

The shipped skills under `src/main/skills/` route agents to authoritative Gradle documentation through `gradle_docs`, but the authored references are inconsistent about pointing readers to the relevant documentation pages. Much of the guidance in the hub skills describes topics that the official Gradle documentation covers in depth, yet the references do not consistently carry a "find out more" pointer to the relevant page — including guidance that was not itself taken from the documentation. The earlier archived tool-first change (`archive/2026-08-01-tool-first-gradle-docs-skills/`) required a lookup ladder, corrected tag-scoping guidance, and a verifier; its checked-off tasks did not durably land. In particular, `using-gradle/references/research.md` still says that every `gradle_docs` call MUST be scoped with a tag, although path reads and the no-argument section browse take no tag, and it has no lookup ladder.

This change was originally framed as a provenance rule — "every citation unit carrying documentation-derived guidance must cite its source." That predicate is too narrow and is the wrong trigger: it requires a link only where the local prose originated from the documentation, and it leaves original prose with no pointer even when a directly relevant documentation page exists. The convention is reframed here as topical navigation: every meaningful topic or area of guidance carries a link to the relevant Gradle documentation page as a "find out more / see for details" pointer, regardless of where the local prose came from. Provenance becomes a natural subset — if guidance did come from a documentation page, that page is by definition a relevant read-more link — but the trigger is topical relevance, not documentation-derived origin.

The two hub skills (`using-gradle` and `authoring-gradle-builds`) carry the main documentation surface. `interacting-with-project-runtime` and `verifying-compose-ui` are runtime skills; under the topical test they receive a link only where a genuinely relevant Gradle documentation topic exists for the area, and no manufactured link otherwise. The `authoring-gradle-builds/references/best-practices/` corpus is frozen generated content; `GenerateBestPracticesDoc` already emits per-topic canonical read links and is a reference implementation only.

The user decision is binding: "We likely don't want automated checks or anything like that for this - they won't be good enough. And since we are using skill instructions rather than direct links, I'm not sure we can check their liveness, although it would be nice." Accordingly, enforcement is convention-based: authoring guidance plus human review. No verifier, Gradle check task, or mechanical citation test is revived; liveness checking is optional future work.

## Goals / Non-Goals

**Goals:**

- Establish one cross-skill `skill-doc-link-convention` capability with review-validated authoring requirements.
- Reframe coverage as topical navigation: every guidance topic carries a relevant canonical `gradle_docs` "find out more" link, with documentation provenance treated as a subset rather than the trigger.
- Standardize links as literal, backticked `gradle_docs` tool-call hints that are directly followable and match generated output.
- Restore the lookup ladder and correct tag-scoping and version-override guidance in `research.md`.
- Define per-file placement and coverage rules — inline at points of need required, with a trailing topical index optional — while preserving local references and non-documentation URLs.
- Treat the generated best-practices corpus as confirm-only and out of scope for the clean-link requirement, confirming it already emits canonical per-topic links without changing or regenerating it.

**Non-Goals:**

- No automated verifier, new Gradle check task, mechanical citation test, or required liveness check.
- No edits to the frozen generated corpus, generator code, synced specs, tool source, or unrelated skills beyond the review described by the phase plan.
- No manufactured links for runtime-skill areas that have no genuinely relevant Gradle documentation topic, and no conversion of local cross-references or non-documentation external URLs.
- No substantive changes to the existing hub capability requirements; their exclusive routing, lookup-ladder, and version-override requirements already exist.

## Decisions

### D1 - Coverage trigger is topical navigation, not provenance

The unit of coverage is renamed from "citation unit" (the smallest self-contained documentation-derived claim) to **guidance topic**: the smallest coherent topic or area of guidance a reader would want to explore further in the official documentation. Practically this is a section, subsection, paragraph, bullet, or table row carrying a distinct topic. The definition is deliberately flexible so authors are not forced to fragment every sentence, but it prohibits one unrelated link standing in for an entire multi-topic section. The trigger for requiring a link is topical relevance — whether a relevant Gradle documentation topic exists for the guidance — not whether the local prose originated from the documentation. Documentation provenance is retained as an explanatory subset: where guidance did come from a documentation page, that page is by definition a relevant read-more link and therefore satisfies the rule. This reframes the "why" while leaving the settled link mechanics unchanged.

### D2 - Canonical documentation-link representation

A documentation link is the literal `gradle_docs` tool-call hint rendered in prose and backticked. Use the general clean-path form `gradle_docs(path="<clean .md path>")` when a specific page is known — illustrated by the common example `gradle_docs(path="userguide/<page>.md")` — with a clean `.md` path and no `#fragment`, `?query`, or `version`. The clean path is the general shape; a valid page path may carry the `userguide/`, `dsl/`, `kotlin-dsl/`, `javadoc/`, or `samples/` prefix, or be the root `release-notes.md`, and `userguide/<page>.md` is only the illustrative common example, not the only allowed shape. The omitted version follows the tool's resolution chain: explicit `version` first, then wrapper auto-detection via `projectRoot` or `GRADLE_MCP_PROJECT_ROOT`, then the latest-stable fallback. Wrapper detection can fail when there is no usable project root or wrapper, so omission does not guarantee wrapper resolution. Authors normally omit `version` and add `version="X.Y"` only when researching a target intentionally different from the wrapper, with a one-line migration or verification note. Use `gradle_docs(query="tag:<tag> <term>")` when a topic is best entered by search. Stored search links are tag-scoped for precision; this governs stored links only and does not constrain runtime lookup-ladder searches, whose broaden step may drop the tag, consistent with the `using-gradle` "Escalate a documentation lookup" ladder. Path reads and the no-argument section browse take no tag. Legacy prose such as a separate tool name, tag, and path is normalized to the call form so authored and generated content share one directly followable rendering.

### D3 - Cardinality and placement: per-topic links, inline versus trailing index

Each guidance topic carries at least one relevant canonical link; a topic that genuinely spans distinct Gradle documentation areas carries one link per relevant area. This replaces the old provenance rule of "exactly one link per distinct documentation source," which survives only as the sub-rule for genuine multi-area topics. Placement is inline-required and trailing-optional: canonical links SHALL be placed inline at points of need, where a specific topic is discussed; a trailing `More info` (or `See also`) block is an optional canonical topical "find out more" index that aggregates the reference's relevant documentation pages. An inline link repeated in the trailing index block is permitted aggregation, because the block is an index rather than a second assertion; two identical pointers repeated at the same inline point of need, with no aggregating block, are pointless duplication and are disallowed.

### D4 - One cross-cutting capability

Create one new `skill-doc-link-convention` capability with ADDED requirements only; the name is kept for continuity. Do not add MODIFIED deltas to hub capabilities: their existing authoritative-routing requirements already mandate exclusive `gradle_docs` routing, the lookup ladder, version overrides, and no blocked documentation URLs. A single cross-skill capability covers both hubs, runtime review, and the generated corpus, and mirrors existing cross-skill capabilities such as `skill-reference-discoverability` and `skill-metadata`. No other capability is touched.

### D5 - Runtime-skill review test is topical existence

The two runtime skills (`interacting-with-project-runtime`, `verifying-compose-ui`) receive a review. Under the topical framing the test is "does a relevant Gradle documentation topic exist for this area," not "was the prose documentation-derived." Where no genuinely relevant Gradle documentation topic exists, no link is added and the "no link warranted" outcome is recorded; a relevant topic, if one exists, earns a link regardless of the prose's origin.

### D6 - Generated corpus is confirm-only and outside the canonical-link requirement

The canonical-link requirement applies to authored skill content (`SKILL.md` bodies and authored references) and to future generator output; it does not require the frozen generated best-practices corpus to be clean. `GenerateBestPracticesDoc` is the canonical-form reference implementation: it emits per-topic `gradle_docs(path="<clean .md path>")` links, strips blocked documentation hosts, and appends the best-practices `gradle_docs` footer. The frozen corpus is confirm-only: make no generator change and do not regenerate or hand-edit it, and any future generator change must preserve the per-topic canonical emission. A known malformed generated javadoc member-anchor hint is disclosed as a grandfathered frozen exception — recorded for reviewer awareness, not fixed, not regenerated, and not hand-edited. Confirmation passes by verifying the generator already emits the canonical form for new output and by recording this frozen exception, without editing or regenerating the corpus.

### D7 - Review-based enforcement

Sustainability is one authoring convention, embedded lookup and link guidance in `research.md`, and human review. The reviewer checklist confirms that every guidance topic has at least one relevant canonical link, with one link per distinct relevant documentation area; that inline-plus-trailing-index repetition is aggregation while same-spot inline repetition is duplication; and that links use clean `.md` call form, omit versions unless a different target is intentional, follow the corrected tag rule and ladder, contain no blocked documentation URLs, add no manufactured runtime links, and leave frozen content byte-identical. `./gradlew :check` and `openspec validate --strict` are ordinary regression-safety gates only, not link validators. Liveness checking is optional future work.

### D8 - Sequencing with the now-archived change

The `integrate-best-practices-recommendations-into-skills` change has already been archived (`archive/2026-08-02-integrate-best-practices-recommendations-into-skills/`), so the earlier "confirm archive/merge order before editing shared hub files" gate is stale and is dropped. The hub-skill files are now safe to edit directly. Implementation still batches disjoint file sets where practical to keep reviews focused, and no archived change is reopened.

## Risks / Trade-offs

- [Risk] Human review can miss a guidance topic or leave a stale link. -> Mitigation: keep the guidance-topic definition, per-area cardinality, aggregation-versus-duplication rule, and checklist explicit in the capability, design, and tasks; do not pretend a mechanical gate can establish semantic coverage.
- [Risk] Topical relevance is a judgment call and reviewers may disagree on granularity. -> Mitigation: anchor the unit to concrete forms (section, subsection, paragraph, bullet, table row) and prohibit one unrelated link standing in for a multi-topic section; record the judgment in review notes.
- [Risk] Link hints may become stale because liveness is not required. -> Mitigation: keep links in directly followable canonical forms and record liveness checking as optional future work.
- [Risk] Existing spec/code drift asserts `verifySkillsMaterialized`, `checkReferenceReachability`, and `checkNoBlockedDocUrls` enforcement that is absent from code. -> Mitigation: this change deliberately does not revive that machinery or compound the drift; a separate change should reconcile or retire those assertions.
- [Trade-off] Runtime skills receive review but likely no links. -> Mitigation: require links only where a genuinely relevant Gradle documentation topic exists and explicitly record when none is warranted.

## Migration Plan

1. Inventory coverage gaps by guidance topic across the shipped references, modifying no files.
2. Repair `research.md` (tag correction, lookup ladder, version-override prose, canonical call form).
3. Perform the authored-reference coverage pass on the two hub skills, adding relevant topical links and normalizing legacy citations, excluding the frozen corpus.
4. Review the runtime skills and confirm generator behavior and frozen-corpus identity without regeneration.
5. Run the human checklist and standard regression gates before archive preparation.

There is no runtime migration or rollback procedure: if review rejects the convention, revise or discard this unarchived change directory without touching shipped content.

## Open Questions

- There are no open questions; liveness checking remains a non-goal captured as optional future work in Non-Goals and the risk mitigations above.
