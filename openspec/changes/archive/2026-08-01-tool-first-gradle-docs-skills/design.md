## Context

The `gradle_docs` tool (`GradleDocsTools.kt` → `GradleDocsService` → `GradleDocsIndexService`/`ContentExtractorService`/`HtmlConverter`/`MarkdownService`) downloads the official Gradle docs ZIP per resolved version, converts HTML→Markdown with flexmark, and serves pages by relative path from a per-version `converted/` directory. Search results carry clean `.md` paths without fragments. Two skill capabilities (`using-gradle`, `authoring-gradle-builds`) currently mandate, in their "Authoritative Documentation Routing" requirements, a dual form: a version-scoped `gradle_docs` tag/path hint **plus** a published `docs.gradle.org` URL, and separately a `gradle-mcp.rnett.dev/latest/tools/` pointer for related MCP tools. The generated best-practices corpus (`GenerateBestPracticesDoc`) emits links as `](${docs.gradle.org URL}) (Use gradle_docs(path="${docPath}").)` where `docPath` frequently carries an HTML-style fragment.

Verified code facts that shape this design:

- `MarkdownService.convertHtml` sets `FlexmarkHtmlConverter.OUTPUT_ATTRIBUTES_ID = false`. Element `id` attributes are therefore **dropped** during conversion; converted markdown headings are plain (`## Continuous build`) with no surviving anchor.
- Gradle userguide HTML puts the anchor id **on the heading element**: `<h2 id="sec:continuous_build">…</h2>`. So the fragment→heading mapping exists in the source HTML and is recoverable if ids are preserved at conversion.
- `GradleDocsService.getDocsPageContent` normalizes `.html`→`.md` **only when the path ends in `.html`/`.md`** (`isHtmlPath`). A path ending in `#fragment` fails that test, is resolved literally, and throws `Docs page not found`. This is the bug the pre-baked hints trip.
- `SkillMaterialization.verify` aggregates `checkInventory`, `checkProvenanceHeaders`, `checkSharedFanOut`, `checkGeneratedContent`, `checkReferenceReachability`; helpers `allMarkdownFiles(dir)` and `isExternalLink(target)` already exist.
- URL inventory across `src/main/skills` (137 URL-bearing locations): the only **documentation-citation** hosts are `docs.gradle.org` and `gradle-mcp.rnett.dev`. Other URLs are legitimate and not replaceable by `gradle_docs`: the `author: https://github.com/rnett/gradle-mcp` skill-metadata field, Maven Central Portal guides (`central.sonatype.org`, `ossrh-staging-api.central.sonatype.com`), and example/license URLs inside code snippets (`github.com/example/library`, `apache.org/licenses/LICENSE-2.0.txt`).

**Stakeholders:** AI agents are the only skill consumers (per project memory: they "live in the moment" and have no backwards-compatibility concern). This is what makes a hard tool-only cutover safe.

## Goals / Non-Goals

**Goals:**
- Skills route to Gradle documentation exclusively through `gradle_docs`; no `docs.gradle.org` or `gradle-mcp.rnett.dev` URL survives in any skill file, enforced by a verifier.
- `gradle_docs` genuinely **supports** `#fragment` (resolves to a section) and explicitly defines `?query` behavior, so fragment-bearing hints resolve rather than throw.
- The `version` parameter description accurately documents the resolution chain; skill prose teaches when to override `version` and when to rely on auto-detection.
- A bounded set of skill-prose improvements (Lookup Ladder, tag-rule fix) consistent with the tool-only direction.

**Non-Goals:**
- No-arg section-summary enrichment with example page paths (user declined; YAGNI — search already returns clean paths).
- A generated "docs navigation index" reference (rejected: duplicates the tool, drifts per version, and contradicts the project's explicit move away from generated root indexes).
- Any change to search indexing, pagination, or version resolution mechanics beyond the `version` `@Description` text.
- Touching the non-documentation external URLs (Central Portal, author metadata, code-snippet examples).

## Decisions

### D1 — Fragment/query semantics: preserve ids at conversion, resolve to section, throw on unresolvable

`getDocsPageContent` parses `path` into `basePath` + optional `#fragment` + optional `?query`:

1. **`?query`**: not meaningful for a filesystem-backed page read. Strip it and append a one-line note to the output (`(query string "..."; ignored for documentation page reads)`). No query semantics are defined. *Alternative considered:* mapping `?query` onto a search — rejected; `query` is already a separate tool parameter, and overloading `path?query` would be confusing and redundant.
2. **`basePath` normalization**: with the fragment/query removed, `basePath` now ends in `.html`/`.md`, so the existing `isHtmlPath` → `.html`→`.md` normalization works unchanged.
3. **No fragment** → return the whole page (current behavior, unchanged).
4. **Fragment present** → section resolution, in priority order:
   - **(a) Anchor-id match (primary).** Preserve element ids during conversion (flip `MarkdownService.convertHtml` to emit ids, e.g. `OUTPUT_ATTRIBUTES_ID = true` or an equivalent `{#id}`/anchor marker). At read time, locate the markdown element bearing the literal fragment id and return the section from that element up to (but not including) the next heading of the same or higher level, prefixed with a short header naming the resolved fragment. This resolves the real pre-baked shapes — `#sec:exclude-trans-deps`, `#config_cache`, `#applying_plugins` — because those ids sit on Gradle headings.
   - **(b) Heading-slug match (best-effort).** If no element carries the literal id, slugify each heading (GitHub-style) and match the fragment as a slug; on match, return that section. Covers callers that pass a heading-text slug rather than the HTML id.
   - **(c) Unresolvable fragment → throw.** Throw a clear, actionable error whose message names both the requested page path and the unresolved fragment (e.g. `Fragment "#x" could not be resolved in page "userguide/y.md"`). **User decision (final):** fail loudly rather than degrade to the full page. *Consequence:* this removes graceful degradation — a stale or mistyped hint now fails at call time instead of silently returning the whole page. That raises the stakes of the id-preservation work (task 1.1): if heading ids are not preserved cleanly, real pre-baked hints such as `#sec:exclude-trans-deps` would throw rather than degrade. Task 1.1 is therefore a hard gate that must verify the existing generated best-practices fragment hints actually resolve before this throw contract ships. *Alternative considered (rejected by the user):* returning the full page with a note.

**Why preserve ids rather than slug-match only:** the acceptance fragments (`sec:exclude-trans-deps`, javadoc member anchors like `afterEvaluate(org.gradle.api.Action)`) are **not** slugs of their heading text, so slug-matching alone fails the stated acceptance test. Id preservation is the only mechanism that resolves them.

**Javadoc fragments** (`#afterEvaluate(org.gradle.api.Action)`, parentheses and all) are member anchors, often not a clean "heading + section" structure. They are handled best-effort by (a)/(b); if they land on a non-heading anchor that neither preserves an id nor slug-matches, (c) throws. Because javadoc member anchors are the shapes most likely to miss id preservation, any javadoc fragment hint shipped into skills must be confirmed resolvable under task 1.1 before the throw contract ships. This limitation is documented rather than over-engineered.

### D2 — Verifier rule: host blocklist, not a blanket URL ban

`SkillMaterialization.verify` gains `checkNoBlockedDocUrls(skillsDir)`: scan every skill markdown file (via the existing `allMarkdownFiles`) for URLs whose host is exactly `docs.gradle.org` or `gradle-mcp.rnett.dev`; report each as a violation. Everything else passes.

**Why a blocklist and not "ban all http(s)":** a blanket ban false-positives on legitimate, non-documentation URLs that are *not* replaceable by `gradle_docs` — the `author:` metadata URL (`github.com/rnett/gradle-mcp`), the Maven Central Portal publishing guides (`central.sonatype.org`), and example/license URLs inside code snippets. A two-host blocklist matches the two decisions precisely and cannot false-positive on those. *Alternative considered:* an allowlist of every permitted host — rejected as brittle (every new legitimate external link would require a verifier edit). The blocklist is the minimal rule faithful to scope.

### D3 — Version override is skill-prose; the only tool change is the `@Description` text

`GradleDocsTools.resolveVersion` already implements: explicit `version` wins → else auto-detect the wrapper from `projectRoot` → else latest-stable from `services.gradle.org` (fallback `BuildConfig.GRADLE_VERSION`). No tool *behavior* change is needed for the override workflow. The change is: (1) correct the `version` `@Description` to name the latest-stable terminal fallback (triggers `:updateToolsList`), and (2) add skill prose to `research.md` and the `authoring-gradle-builds` upgrading docs: omit `version` by default; pass explicit `version="X.Y"` only when the target differs from the wrapper (migration; verifying a specific minor); keep the anti-patterns (coarse `"8"` fails; don't silently use current/latest when the wrapper is older).

### D4 — Generator emits tool hint only, with a clean path

`GenerateBestPracticesDoc.normalizeInternalLinks` emits the link text followed by `(Use gradle_docs(path="<clean .md path>").)` — dropping both the `docs.gradle.org` URL and any `gradle-mcp.rnett.dev` URL, and stripping fragment/query from the emitted `docPath`. Regeneration re-stamps the `hash` header that `checkGeneratedContent` validates and the `gradle-version` field it checks. (Fragment support in the tool is what makes it safe to also keep fragments out of the regenerated hints: the tool now tolerates either form.)

### D5 — OpenSpec reconciliation and sequencing

The documentation-URL mandates live in the **in-flight, complete-but-un-archived** change `redesign-gradle-skills-portfolio`, whose delta specs define `using-gradle`, `authoring-gradle-builds`, and `skill-infrastructure`. Those capabilities are therefore **not yet** in `openspec/specs/`. This change writes MODIFIED deltas against the requirement text that change produces:

- `using-gradle` → MODIFIED `Authoritative Documentation Routing`: rewrite the "Research a major Gradle topic" scenario to provide a version-scoped `gradle_docs` tag/path hint and route exclusively through `gradle_docs`; **remove** the "Use a related MCP tool" scenario (the `gradle-mcp.rnett.dev` pointer); keep "Follow cross-topic references" but strip its URL wording.
- `authoring-gradle-builds` → MODIFIED `Authoritative Documentation Routing`: remove "and published `docs.gradle.org` URL" from the requirement and the `docs.gradle.org`/`gradle-mcp.rnett.dev` scenarios; keep verified `gradle_docs` tag/path hints.
- `skill-infrastructure` → MODIFIED `Materialization Validation`: add the documentation-URL blocklist invariant to what `verifySkillsMaterialized` enforces.
- `gradle-docs-querying` (live spec) → ADDED `Fragment and query handling for page reads` and ADDED `Accurate version parameter documentation`.

**Sequencing constraint:** because the three skill capabilities exist only as deltas in `redesign-gradle-skills-portfolio`, that change must be archived first (creating the base specs) so this change's MODIFIED deltas apply cleanly; alternatively the two changes are archived together via the bulk-archive flow, which resolves overlapping capability deltas against implementation evidence. OpenSpec `validate --strict` does not require a MODIFIED requirement to pre-exist in the base spec (verified: `redesign-gradle-skills-portfolio` validates strict with MODIFIED requirements whose capabilities are not in `openspec/specs/`), so this change validates independently regardless of archive order.

## Risks / Trade-offs

- **[Id-preservation changes all converted markdown]** → flipping `OUTPUT_ATTRIBUTES_ID` alters every generated page, so all seven `HtmlConverterTest` snapshots change and the best-practices content changes (hashes re-stamp — already required by D4). *Mitigation:* sequence the converter change first; update the snapshots deliberately; gate on `:check`.
- **[Exact flexmark id output format is unverified]** → flexmark may emit heading ids as a `{#id}` suffix, an inline `<a id>`, or only for some element kinds; the section-extraction parser in D1(a) depends on the precise format, and empty `<a id>` anchors (vs heading ids) may need a jsoup pre-pass or a custom flexmark extension. *Mitigation:* treat "confirm flexmark id format and anchor coverage" as the first implementation sub-task and a hard gate before writing the extractor; if heading-id coverage is incomplete, fall back to D1(b)/(c) for the affected shapes and document it.
- **[Javadoc member-anchor fragments resolve weakly]** → parenthesized member anchors are not clean heading sections, and under the throw contract an unresolved javadoc fragment fails loudly rather than degrading. *Mitigation:* best-effort via D1(a)/(b); task 1.1 must confirm every javadoc fragment hint shipped into skills resolves before the throw contract ships; document the limitation in the tool description and `research.md`.
- **[Two active changes touch the same three capabilities]** → archive-order coupling. *Mitigation:* D5 sequencing note; archive `redesign-gradle-skills-portfolio` first or bulk-archive together.
- **[Throw contract removes graceful degradation]** → a stale or mistyped fragment hint now fails loudly instead of returning the page, so a bad hint in shipped skill content is a hard error at call time. *Mitigation:* task 1.1 is a hard gate that verifies all existing generated best-practices fragment hints resolve before the throw behavior ships; the error message names the page path and the unresolved fragment so the failure is actionable. This is the user-chosen contract (fail loudly over silent degradation).

## Migration Plan

1. Tool fragment/query support + id preservation + tests + `version` `@Description`; run `:updateToolsList`.
2. Generator link-normalization change; regenerate best-practices (re-stamps hashes).
3. Authored skill edits: strip both URL host classes; fix the tag-mandate rule; add the Lookup Ladder and version-override guidance.
4. Verifier blocklist check + test (added only after content is clean, so it passes).
5. OpenSpec spec reconciliations (this proposal's deltas; applied at archive time).
6. Gate: `./gradlew :check`.

**Rollback:** each step is independently revertible. The verifier (step 4) is deliberately last so that reverting content edits without reverting the verifier cannot occur; if content must be rolled back, revert the verifier in the same change.

## Open Questions

- **Unresolvable-fragment contract (D1c): RESOLVED — throw.** The user chose fail-loudly: an unresolvable fragment throws an error naming the page path and the fragment, rather than degrading to the full page. The consequence is captured in D1(c) and Risks; task 1.1 is the hard gate that makes this safe to ship.
- **Archive ordering: DEFERRED (user decides later).** Confirm `redesign-gradle-skills-portfolio` archives before this change (or that both are bulk-archived together), so the three shared-capability deltas reconcile cleanly. Strict validation passes regardless; this only affects spec sync at archive time.
- **flexmark id mechanism (D1a):** the implementation must confirm whether `OUTPUT_ATTRIBUTES_ID = true` preserves the heading ids this design relies on, or whether a jsoup pre-pass / custom extension is required. This is an implementation-phase research prerequisite, flagged here so it is not silently skipped.
