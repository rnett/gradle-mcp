## Why

The Gradle skills currently route agents to documentation through three channels at once: a `gradle_docs` tool hint, a published `docs.gradle.org/current/...` URL, and a `gradle-mcp.rnett.dev/latest/tools/...` tool-doc pointer. The two URL channels are liabilities for an agent-only consumer. The `docs.gradle.org/current/` URL is version-ambiguous (always `current`, never the project's wrapper version), needs web access the agent may lack, and duplicates the version-correct hint that already precedes it. The `gradle-mcp.rnett.dev` tool-doc pointers are worse than useless: those pages are mechanical exports of the tool descriptions the agent already has in-context, so reading them costs tokens and adds nothing. Consolidating to the `gradle_docs` tool alone makes every lookup version-correct by construction and removes dead weight from skill context.

Two correctness gaps block a clean tool-only posture. First, the tool does not understand `#fragment`/`?query` in `path`: the generated best-practices hints embed HTML-style fragments (e.g. `userguide/resolution_rules.html#sec:exclude-trans-deps`), and `getDocsPageContent` resolves them literally and throws "Docs page not found". Removing the URL fallback is exactly what exposes these broken hints, so the tool must genuinely *support* fragments (resolve them to a section), not merely tolerate them. Second, the `version` parameter description understates the real fallback chain (it omits the latest-stable terminal fallback), which misleads agents doing version-override research.

## What Changes

- **BREAKING** (skill content): Skills route to Gradle documentation **exclusively** through the `gradle_docs` tool. Remove all `docs.gradle.org` URLs **and** all `gradle-mcp.rnett.dev/latest/tools/...` tool-doc pointers from every skill file (authored references and generated best-practices). Non-documentation external URLs are retained (e.g. Maven Central Portal guides, the `author:` metadata URL, license/example URLs inside code snippets).
- **Tool: fragment and query support.** `gradle_docs` `path` parsing accepts an optional `#fragment` and `?query`. `?query` is ignored for page reads (noted in output). A `#fragment` resolves to the corresponding section: HTML anchor ids are preserved during HTML→Markdown conversion so fragments like `#sec:exclude-trans-deps` map to their heading; an unresolvable fragment throws an error naming the page path and the fragment (fail loudly rather than degrade to the full page).
- **Tool: `version` description correction.** Correct the `version` parameter `@Description` to state the full resolution chain (explicit → wrapper auto-detect → latest-stable fallback), not only "detected project version". Triggers `:updateToolsList`.
- **Skill prose: explicit version override.** Teach agents to omit `version` by default (resolves to the wrapper) and to pass an explicit `version="X.Y"` only when researching a version that differs from the wrapper (the upgrading/migration workflow; verifying a specific minor release). Reinforce the existing anti-patterns (a coarse `"8"` fails; silently using current/latest when the wrapper is older is wrong).
- **Skill prose: Documentation Lookup Ladder + tag-rule fix.** Add a compact escalation path to `research.md` (scoped search → broaden → browse tree → read page; no-arg call lists sections) and correct the false "Every call MUST be scoped with a tag" rule (path reads and the no-arg browse take no tag).
- **Generator change.** `GenerateBestPracticesDoc.normalizeInternalLinks` emits link text + a `gradle_docs(path=...)` hint only — no `docs.gradle.org` URL, no `gradle-mcp.rnett.dev` URL — with a clean `.md` path (no fragment/query). Regeneration re-stamps the content hashes validated by `checkGeneratedContent`.
- **Verifier.** `SkillMaterialization.verify` gains a check that fails the build if any skill markdown file contains a URL whose host is `docs.gradle.org` or `gradle-mcp.rnett.dev`. Sequenced after content is clean.

## Capabilities

### New Capabilities
<!-- None. All changes modify existing or in-flight capabilities. -->

### Modified Capabilities
- `gradle-docs-querying`: ADDED requirements for fragment/query handling on page reads and for accurate `version` parameter documentation. (Live spec in `openspec/specs/`.)
- `using-gradle`: MODIFIED "Authoritative Documentation Routing" to route exclusively through `gradle_docs` (drop the `docs.gradle.org` URL scenario and the `gradle-mcp.rnett.dev` MCP-tool-pointer scenario).
- `authoring-gradle-builds`: MODIFIED "Authoritative Documentation Routing" to drop the published-URL mandate and the MCP-tool-pointer scenario, keeping verified `gradle_docs` tag/path hints.
- `skill-infrastructure`: MODIFIED "Materialization Validation" to add the documentation-URL blocklist invariant to `verifySkillsMaterialized`.

> **Sequencing note:** `using-gradle`, `authoring-gradle-builds`, and `skill-infrastructure` do not yet exist in `openspec/specs/`; they are introduced by the in-flight (complete, un-archived) change `redesign-gradle-skills-portfolio`. This change's deltas for those three capabilities are written against the requirement text that change produces, so `redesign-gradle-skills-portfolio` must archive before (or be reconciled with) this change. See design.md.

## Impact

- **Tool source** (`src/main/kotlin/.../docs/`): `MarkdownService.convertHtml` (preserve element ids), `GradleDocsService.getDocsPageContent` (parse fragment/query, section extraction, unresolvable-fragment error), `GradleDocsTools.kt` (`version` `@Description`). Triggers `:updateToolsList`.
- **Generator** (`best-practices-generator/.../GenerateBestPracticesDoc.kt`): link normalization; regenerate best-practices content (re-stamps hashes).
- **Skill content** (`src/main/skills/using-gradle`, `src/main/skills/authoring-gradle-builds`): strip both URL host classes from ~26 authored references and the SKILL.md bodies; fix the tag-mandate rule; add the Lookup Ladder and version-override guidance; regenerate ~35 best-practices references.
- **Verifier** (`src/main/kotlin/.../skills/SkillMaterialization.kt`) and its test.
- **Tests**: `GradleDocsServiceTest` (fragment→section, HTML-anchor resolves, unresolvable fragment throws, `?query` ignored), `HtmlConverterTest` snapshots (id preservation changes output), `GenerateBestPracticesDocTest`, `SkillMaterializationTest`.
- **Gate**: `./gradlew :check` (runs `verifySkillsMaterialized` + `verifyToolsList`).
