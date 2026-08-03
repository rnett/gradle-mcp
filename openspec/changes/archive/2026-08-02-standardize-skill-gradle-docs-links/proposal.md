## Why

The shipped Gradle skills do not consistently point readers to the relevant Gradle documentation pages. The references describe many topics that the official documentation covers in depth, yet they often carry no "find out more" pointer — including guidance that was not itself taken from the documentation. The earlier tool-first change was archived without durably landing its lookup-ladder and tag-correction guidance, leaving false tag-scoping prose and inconsistent documentation links. This change establishes a topical documentation-link convention: every meaningful topic or area of guidance in a skill carries a link to the relevant Gradle documentation page, regardless of where the local prose originated, enforced by authoring convention and human review rather than automated checks.

## What Changes

- Establish one cross-skill convention for canonical documentation links rendered as backticked `gradle_docs` tool-call hints.
- Normalize known-page, search, and explicit-version link forms, including the correction that only searches take `tag:<tag>` scoping.
- Reframe coverage as topical navigation: every guidance topic in an authored reference for which a relevant Gradle documentation topic exists carries at least one relevant canonical link as a "find out more / see for details" pointer, and no manufactured link is added where no genuinely relevant topic exists. A guidance topic is the smallest coherent topic or area of guidance a reader would want to explore further — a section, subsection, paragraph, bullet, or table row carrying a distinct topic, never a multi-topic section behind one unrelated link. The trigger is topical relevance, not documentation-derived origin; provenance is a natural subset. A topic spanning distinct documentation areas carries one link per relevant area; inline links repeated in a trailing `More info` index are permitted aggregation rather than duplication. Preserve local cross-references and non-documentation URLs.
- Add the lookup ladder, corrected tag rule, version-override guidance, and link examples to the authored research guidance.
- Review the two documentation-heavy hub skills and the two runtime skills, adding no link where no genuinely relevant Gradle documentation topic exists.
- Treat the frozen generated best-practices corpus as confirm-only and out of scope for the clean-link requirement: confirm it already emits per-topic canonical read links for future output, and make no generator change or regeneration. Record the known malformed javadoc-member-fragment as a grandfathered frozen exception — recorded, not fixed, not regenerated, and not hand-edited.
- **BREAKING**: Change authored skill documentation-link prose to the canonical call form and remove legacy documentation-link rendering where encountered.
- Use convention, embedded guidance, and human review as enforcement; add no verifier, Gradle check task, or mechanical citation test. Record liveness checking as optional future work.

## Capabilities

### New Capabilities

- `skill-doc-link-convention`: Cross-skill authoring requirements for canonical `gradle_docs` links, topical coverage of guidance topics, per-file link structure, human-reviewed coverage, and preservation of genuine non-documentation links.

### Modified Capabilities

<!-- No existing capability requirements are modified; the hub requirements already cover exclusive documentation routing, the lookup ladder, and version overrides. -->

## Impact

- Authored content under `src/main/skills/using-gradle/` and `src/main/skills/authoring-gradle-builds/` will be reviewed and normalized to carry relevant topical links, excluding the frozen generated best-practices corpus.
- `src/main/skills/interacting-with-project-runtime/` and `src/main/skills/verifying-compose-ui/` will receive a review for genuinely relevant Gradle documentation topics, with no manufactured links.
- `best-practices-generator/` is a confirm-only reference implementation and is not changed or run.
- The new OpenSpec capability delta documents review-validated authoring behavior; standard regression gates remain safety checks and are not link validators.
