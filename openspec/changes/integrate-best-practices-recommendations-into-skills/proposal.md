## Why

`reports/gradle-best-practices-recommendations.md` distills 134 opinionated Gradle 9.x recommendations for agents that run builds or write build logic. Each entry is audience-tagged, severity-graded, grounded in why the guidance is hard to figure out, and paired with do/don't snippets and `gradle_docs` references. The recommendations currently live only in `reports/` and are not shipped to agents.

The shipped skills already carry a FROZEN generated corpus in `authoring-gradle-builds/references/best-practices/*.md`, a snapshot of official documentation. The 134-entry field guide is a complementary opinionated layer that must ride alongside that corpus. The hardest-to-figure-out footguns and High-severity cross-cutting rules belong in always-loaded skill bodies instead of optional references.

## What Changes

- Surface the 13 hardest-to-figure-out highlights and High-severity cross-cutting rules as body-level rules in `using-gradle` (`[Runs builds]`) and `authoring-gradle-builds` (`[Writes build logic]`).
- Place all remaining recommendations and ALL do/don't snippets in non-generated authored references, woven into existing references or added as authored-local files.
- Place and trace all 134 entries, keyed by entry title and audience.
- Preserve `authoring-gradle-builds/references/best-practices/*.md` untouched and cross-link to it using `Index -> Detail -> Gradle Docs` without restating generated detail.
- Mark version-sensitive entries and require a wrapper-version check before applying them; the research used Gradle 9.6.1 documentation while the project targets 9.4.1.
- Allow minimal clear-fit JVM, toolchain, and daemon guidance in the two runtime skills' shared setup reference, as content-only additions within existing requirements.
- Make no tool-source, generator, verifier, or frontmatter/routing changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `gradle-skill-best-practices-integration`: add corpus-preservation, body/reference placement, traceability, and authored-reference materialization requirements; modify `Lookup order documented` to add the authored tier before the frozen corpus escalation.
- `authoring-gradle-builds`: add body footgun rules and authored best-practice references; modify `Best Practices Integration` so authored guidance coexists with and cross-links to, but never restates, the frozen corpus.
- `using-gradle`: add operational footgun body rules and authored operational references.

The following capabilities have no normative delta: `skill-reference-discoverability`, `skill-metadata`, `gradle-best-practices`, `best-practices-tagging`, `skill-infrastructure`, `interacting-with-project-runtime`, and `verifying-compose-ui`. Runtime additions are content-only within existing shared setup and local troubleshooting references.

## Impact

- Skill content in `using-gradle` and `authoring-gradle-builds` gains compact, always-loaded footgun rules.
- Authored references gain woven recommendation guidance and snippets; new authored-local files may be added only where an existing reference has no natural home.
- New authored references require a provenance header and a relative link from `SKILL.md`; they must remain outside the generated `references/best-practices/` directory.
- Optional runtime fits edit `src/main/skill-sources/authored-shared/repl-session-setup.md`, then use `materializeSkills`; materialized copies are not edited directly.
- The source of truth is `reports/gradle-best-practices-recommendations.md`, which is research-only and is not shipped.
- The implementation gate is `./gradlew :check` for materialization and tool-list verification. OpenSpec validation is a separate explicit gate: `openspec validate integrate-best-practices-recommendations-into-skills --strict`.
