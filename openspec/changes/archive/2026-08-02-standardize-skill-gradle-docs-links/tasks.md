## 1. Phase 1 - Audit and inventory

- [x] 1.1 Audit the shipped skill references and inventory each guidance topic that lacks a relevant canonical `gradle_docs` link, grouped by skill and reference; record the distinct relevant documentation areas for multi-area topics and any permitted inline-plus-trailing `More info` aggregation; modify no files.

## 2. Phase 2 - Repair research guidance

- [x] 2.1 Correct `using-gradle/references/research.md` so stored search links are tag-scoped while path reads and the no-argument section browse take no tag, leaving the runtime lookup-ladder broaden step (2.2) free to drop the tag.
- [x] 2.2 Add the Documentation Lookup Ladder: scoped `tag:<tag> <term>` search, broaden by dropping the tag, browse with `path="."`, then read a specific `path`; document that no-argument calls list sections.
- [x] 2.3 Align version-override prose with the tool's resolution chain: explicit `version`, then wrapper auto-detection via `projectRoot` or `GRADLE_MCP_PROJECT_ROOT`, then latest-stable fallback; wrapper detection can fail without a usable project root or wrapper, so authors normally omit `version` and specify `version="X.Y"` only for an intentionally different research target.
- [x] 2.4 Normalize the reference's authoritative-docs and `More info` links to the canonical call form.

## 3. Phase 3 - Authored-reference coverage pass

- [x] 3.1 Normalize legacy documentation links in authored references for `using-gradle` and `authoring-gradle-builds` to clean, backticked `gradle_docs` call hints.
- [x] 3.2 For every inventoried guidance topic, ensure at least one relevant canonical "find out more" link; carry one link per distinct relevant documentation area when a topic genuinely spans multiple areas; and permit an inline link to repeat in the trailing `More info` index block as aggregation rather than duplication.
- [x] 3.3 Preserve local cross-references and non-documentation external URLs, and do not edit or regenerate `authoring-gradle-builds/references/best-practices/`.

## 4. Phase 4 - Runtime-skill review

- [x] 4.1 Review `interacting-with-project-runtime` for any genuinely relevant Gradle documentation topic and add no manufactured link where none exists.
- [x] 4.2 Review `verifying-compose-ui` for any genuinely relevant Gradle documentation topic and add no manufactured link where none exists.
- [x] 4.3 Record the actual result of each runtime-skill review in the change notes or review record: for each skill, either the relevant canonical `gradle_docs` link that was added, or a documented rationale that no genuinely relevant Gradle documentation topic exists.

## 5. Phase 5 - Generator confirmation

- [x] 5.1 Confirm that `GenerateBestPracticesDoc` emits per-topic canonical read links, strips blocked documentation hosts, and emits the best-practices footer; make no generator change.
- [x] 5.2 Confirm the frozen generated corpus is byte-identical without regeneration or hand edits.
- [x] 5.3 Record the known generated javadoc-member-fragment as a disclosed grandfathered frozen exception: it lies outside the normative clean-link requirement (which covers authored content and future generator output), so it is recorded for reviewer awareness and is not fixed, regenerated, or hand-edited in this change.

## 6. Phase 6 - Review-based verification and archive preparation

- [x] 6.1 Run the human-review checklist: every guidance topic has at least one relevant canonical link, with one link per distinct relevant documentation area; inline-plus-trailing `More info` repetition is aggregation while same-spot inline repetition is duplication; links use canonical clean `.md` call form, correct version and tag rules, and the lookup ladder; and there are no blocked documentation URLs, no manufactured runtime links, an unchanged frozen corpus, and the known javadoc-member-fragment recorded as a grandfathered frozen exception rather than a defect requiring a fix.
- [x] 6.2 Run `./gradlew :check` as standard regression safety only; do not treat it as link validation.
- [x] 6.3 Run `openspec validate --strict` as standard OpenSpec regression safety only.
- [x] 6.4 Optionally run `verifySkillsList` manually if useful, without turning it into a required link gate.
- [x] 6.5 Prepare the change for archive only after human review signs off; keep liveness checking as optional future work.
