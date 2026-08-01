## 1. Tool: fragment and query support

- [x] 1.1 Confirm the flexmark id-preservation mechanism: enable `OUTPUT_ATTRIBUTES_ID` (or an equivalent `{#id}`/anchor marker) in `MarkdownService.convertHtml` and verify, against a userguide sample, that heading ids such as `sec:continuous_build` survive in the converted markdown. If heading-id coverage is incomplete, add a jsoup pre-pass or custom flexmark extension. **Hard gate (must pass before 1.3 and before the 1.5 throw contract ships):** verify that every fragment-bearing hint in the currently generated best-practices references (e.g. `#sec:exclude-trans-deps`) resolves to a section under the chosen mechanism; any hint that does not resolve must be fixed (converter or hint text) first, because once 1.5 ships a non-resolving hint throws at call time instead of degrading to the full page.
- [x] 1.2 Update the seven `HtmlConverterTest` snapshot expectations to reflect the id-preserving output.
- [x] 1.3 In `GradleDocsService.getDocsPageContent`, parse `path` into base + optional `#fragment` + optional `?query`; strip `?query` (note it in output) and normalize the now fragment-free base `.html`→`.md`.
- [x] 1.4 Implement section extraction: on a fragment, match a preserved anchor id first, then a heading-text slug; return the section from that heading to the next same-or-higher-level heading, prefixed with a header naming the resolved fragment.
- [x] 1.5 Implement the unresolvable-fragment error: throw a clear, actionable error whose message names both the requested page path and the unresolved fragment (do not degrade to the full page). Ship only after the 1.1 hard gate passes.
- [x] 1.6 Add `GradleDocsServiceTest` cases: fragment→section, HTML-anchor id resolves (`#sec:...`), heading-slug resolves, unresolvable fragment throws an error whose message names the page path and the fragment, `?query` ignored with note, `.html#fragment` normalizes, and no-fragment behavior is unchanged.

## 2. Tool: `version` description correction

- [x] 2.1 Correct the `version` parameter `@Description` in `GradleDocsTools.kt` to state the full chain (explicit → wrapper auto-detect via `projectRoot` → latest-stable fallback), removing the implication that project detection is terminal.
- [x] 2.2 Run `./gradlew :updateToolsList` to regenerate `docs/tools/*.md`.

## 3. Generator: tool-hint-only links

- [x] 3.1 Change `GenerateBestPracticesDoc.normalizeInternalLinks` to emit link text + `(Use gradle_docs(path="<clean .md path>").)` only — drop the `docs.gradle.org` URL and any `gradle-mcp.rnett.dev` URL, and strip fragment/query from the emitted path.
- [x] 3.2 Update `GenerateBestPracticesDocTest` expectations for the new link format.
- [x] 3.3 Run `generateBestPracticesDoc` to regenerate the best-practices references (re-stamps the `hash` and `gradle-version` headers validated by `checkGeneratedContent`).

## 4. Authored skill content

- [x] 4.1 Strip every `docs.gradle.org` and `gradle-mcp.rnett.dev/latest/tools/...` URL from the `using-gradle` authored references and `SKILL.md` (keep the `gradle_docs` tag/path hints; keep non-documentation URLs such as Central Portal and the `author:` metadata).
- [x] 4.2 Strip every `docs.gradle.org` and `gradle-mcp.rnett.dev/latest/tools/...` URL from the `authoring-gradle-builds` authored references and `SKILL.md`.
- [x] 4.3 In `using-gradle/references/research.md`, fix the false "Every call MUST be scoped with a tag" rule: scope *searches* with a tag; `path` reads and the no-arg browse take none.
- [x] 4.4 In `research.md`, add the Documentation Lookup Ladder (scoped `tag:<tag> <term>` → broaden by dropping the tag → browse tree `path="."` → read `path="..."`; no-arg call lists sections).
- [x] 4.5 In `research.md`, reinforce version-override guidance: omit `version` by default (resolves to wrapper); pass explicit `version="X.Y"` only when the target differs from the wrapper; keep the coarse-`"8"` and silent-latest anti-patterns.
- [x] 4.6 In the `authoring-gradle-builds` upgrading/release-notes reference and the `SKILL.md` "Before You Modify" sequence, add version-override guidance for the migration workflow (`tag:upgrading`/`tag:release-notes` with explicit `version="<target>"`).

## 5. Verifier: documentation-URL blocklist

- [x] 5.1 Add `checkNoBlockedDocUrls(skillsDir)` to `SkillMaterialization.verify`, scanning all skill markdown files (via `allMarkdownFiles`) for URLs whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`; wire it into the `verify` aggregation. (Sequenced after sections 3–4 so the tree is clean.)
- [x] 5.2 Add `SkillMaterializationTest` cases: a clean tree passes; an injected `docs.gradle.org` URL fails; an injected `gradle-mcp.rnett.dev` URL fails; a `central.sonatype.org` URL and the `author:` metadata URL pass.

## 6. OpenSpec reconciliation

- [x] 6.1 Confirm the spec deltas in this change (`gradle-docs-querying` ADDED; `using-gradle`, `authoring-gradle-builds`, `skill-infrastructure` MODIFIED) validate via `openspec validate tool-first-gradle-docs-skills --strict`.
- [x] 6.2 Archive `redesign-gradle-skills-portfolio` before this change (or bulk-archive both together) so the three shared-capability deltas reconcile against the implemented content.

## 7. Verification and gate

- [x] 7.1 Run targeted tests: `./gradlew :test --tests "*GradleDocsServiceTest" --tests "*HtmlConverterTest" --tests "*GenerateBestPracticesDocTest" --tests "*SkillMaterializationTest"`.
- [x] 7.2 Smoke-test fragment support: call `gradle_docs(path="userguide/resolution_rules.html#sec:exclude-trans-deps")` and confirm it resolves to the "Excluding transitive dependencies" section; then call with a bogus fragment (e.g. `#no-such-anchor`) and confirm it throws an error naming the page path and the fragment.
- [x] 7.3 Run the project gate `./gradlew :check` (executes `verifySkillsMaterialized`, including the new URL blocklist, and `verifyToolsList`).
