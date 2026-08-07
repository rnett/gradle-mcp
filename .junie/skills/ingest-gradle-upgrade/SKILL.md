---
name: ingest-gradle-upgrade
description: |
  Ingests a Gradle wrapper upgrade for this repository: reads the release notes, upgrade guides, and linked documentation for the version upgraded to, then updates this repo's own code/tooling and the shipped Gradle MCP skills (src/main/skills/*) so everything stays accurate for that version.

  ## Positive Triggers (when to activate)
  - The Gradle wrapper of this repo was just bumped in `gradle/wrapper/gradle-wrapper.properties` (or the bump is in flight) and the new version's implications must be ingested.
  - Asked to "ingest the Gradle upgrade", "sync the repo/skills with Gradle <version>", or "update our skills/docs for the new Gradle version".
  - Auditing whether shipped skills, the generated best-practices corpus, or version-sensitive repo code are still accurate after a wrapper upgrade.

  ## Negative Triggers (when NOT to activate)
  - Performing the wrapper bump itself (make the upgrade, then come here to ingest it).
  - Version-sensitive edits to some other project's build (that is the shipped `authoring-gradle-builds` upgrading-and-release-notes workflow).
  - One-off release-note lookups or documentation questions (call `gradle_docs` directly).
  - Dependency version upgrades, or tool-doc sync (`updateToolsList`) unrelated to a Gradle upgrade.
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.3.1"
---
# Ingest Gradle Upgrade

Post-upgrade maintenance process for this repository. After the Gradle wrapper version is bumped, ingest the release notes, upgrade guides, and linked documentation for the new version, then update (a) this repo's code and tooling, (b) the shipped Gradle MCP skills under `src/main/skills/`, and (c) the generated best-practices corpus, so everything stays accurate for the new version.

**More info**: Do all documentation research through `gradle_docs` pinned to explicit versions: `gradle_docs(path="release-notes.md", version="<v>")`, `gradle_docs(path="userguide/upgrading_version_<N>.md", version="<v>")`, and `gradle_docs(query="tag:<scope> <term>", version="<v>")` with scopes `userguide`, `dsl`, `release-notes`, `best-practices`, `upgrading`, `javadoc`, `samples`. Version resolution is explicit `version` → wrapper auto-detection via `projectRoot` → latest stable; always pass `version` explicitly here, because old and new behavior must be distinguished. Use `gradle_docs(path=".", version="<v>")` to list a version's doc page tree. Sourcing boundary: release enumeration (which versions exist and must be read) comes from the official versions metadata endpoint `https://services.gradle.org/versions/all` (workflow step 2.1) and supplies version metadata only; all documentation content is read exclusively through `gradle_docs`.

## Before You Start

1. Confirm the wrapper bump exists (or is part of this task): `gradle/wrapper/gradle-wrapper.properties` differs from the last recorded version.
2. Establish the OLD version before reading anything — git history of the wrapper properties file is the only reliable source (Workflow step 1).
3. Plan for both halves up front: (a) repo code/tooling and (b) shipped skills. Neither half is optional; a "no impact" conclusion for either still requires explicit triage evidence in the ledger.

## Scope

In scope:
1. This repo's own code and tooling: build scripts, the Tooling API layer, init scripts, docs/version services, tests.
2. Shipped skills under `src/main/skills/`: SKILL.md bodies, references, compatibility tables, lifecycle language, and `gradle_docs` pointers.
3. The generated best-practices corpus — regenerated, never hand-edited.
4. Records: wrapper version line in `.junie/playbook.md`; `Build Verification` record in `AGENTS.md`.

Out of scope: the wrapper bump itself; changes to other projects; new feature work discovered while reading the notes (record as follow-ups, do not implement here).

## Constitution

1. **The wrapper properties file is the NEW-version source of truth.** Parse NEW from `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties`; never take the version from memory, doc prose, or an unrelated Gradle installation.
2. **OLD comes from git history.** `git log -p gradle/wrapper/gradle-wrapper.properties` (previous `distributionUrl`), falling back to `.junie/playbook.md`; otherwise ask. Never guess.
3. **Pin every docs lookup to an explicit version.** `version="<NEW>"` for new docs, `version="<OLD>"` when comparing old behavior; default resolution can silently read the wrong version's docs.
4. **Canonical enumeration only.** The release-notes reading set comes from the official final-releases list (step 2.1); upgrade-guide block selection comes from the anchor rule `OLD < T <= NEW` applied within every spanned version-family guide (step 3.1). Never derive either set from heading prose or memory.
5. **Sourcing boundary.** All documentation content — release notes, upgrade guides, and linked documentation pages — is read exclusively through `gradle_docs`, never from web-search paraphrase. The only sanctioned non-`gradle_docs` source is the official versions metadata endpoint `https://services.gradle.org/versions/all` (step 2.1), which is used solely to enumerate which releases exist; it supplies version metadata and nothing else.
6. **One ledger, one disposition unit.** The ledger's row unit is the material item: every material item in every walked source gets exactly one row. Walked source units that contain zero material items — top-level release-notes sections, in-range upgrade-guide blocks, crossed-major guide sections — each get exactly one sentinel row (`no material items — <reason>`); sentinel rows are coverage evidence, not dispositions. Every change traces to a ledger row.
7. **Regenerate, don't hand-edit, the best-practices corpus.** `generateBestPracticesDoc` owns that content.
8. **Shipped-skill edits follow the shipped-skill rules.** Load [Shipped Skills Update Checklist](references/shipped-skills-update-checklist.md) before touching `src/main/skills/`.
9. **Ingestion ends in verification.** `./gradlew check` green (or green except explicitly named known pre-existing failures), plus an updated `.junie/playbook.md`.
10. **The ingested version is CURRENT, not future.** Facts about NEW behavior that describe the current state of Gradle are written as plain current-state guidance, with no version label. Version anchors (`**Gradle N:**` / `as of N`) are reserved for version-specific facts — behavior only true under the ingested version (deprecation/removal timelines, incubating or lifecycle status, version numbers, transitional previews) — and for past notes about older versions. Never write `N+`-suffixed future-labelled notes (e.g. `**9.7+:**`): an `N+` suffix claims the fact holds for every later version and becomes wrong as soon as the next version ships, while anchored notes age into past notes naturally as later versions ship.

## Materiality and the Ledger Contract

The impact ledger is the single work queue. Its row unit is the **material item** — one row per material item, per walked source:

- **Release notes:** one row per material entry (a leaf entry in any walked section of any release-notes document read).
- **Within-major upgrade-guide blocks:** one row per material item inside each in-range `changes_T` block, across every spanned version-family guide (`upgrading_version_<M>.md` for every major `M` with `major(OLD) <= M <= major(NEW)`). The Source column carries the family page, the block anchor, and the heading text in the form `upgrading_version_<M>.md#changes_<T> (<heading text>)`; for OLD=9.4.1, NEW=9.6.1 the two in-range row groups are sourced `upgrading_version_9.md#changes_9.5.0 (Upgrading from 9.4.0 and earlier)` and `upgrading_version_9.md#changes_9.6.0 (Upgrading from 9.5.0 and earlier)`.
- **Crossed-major guide sections:** one row per material item inside each walked section of each crossed-major guide. The Source column carries the guide and section in the form `upgrading_major_version_<N> (<section heading>)`.
- **Sentinel rows — the sole exception to the item unit.** Every walked source unit that contains zero material items — a top-level release-notes section, an in-range `changes_T` block, or a crossed-major guide section — gets exactly one row reading `no material items — <one-line reason>`. Sentinel rows are coverage evidence that the unit was walked, not dispositions; they never replace item rows where material items exist.

An entry is **material** if it meets ANY of:
1. It changes a Gradle, Tooling, or init-script API/behavior that this repo calls or parses (mapped surfaces: [Release Notes Impact Map](references/release-notes-impact-map.md), Tables A and C).
2. It changes guidance, a compatibility claim, or a `gradle_docs(path=...)` pointer present in `src/main/skills/` (Table A skill column, Table B).
3. It changes a feature's lifecycle status — incubating → stable promotion, deprecation, or removal — for any feature this repo or its shipped skills mention.
4. It fixes an issue this repo works around (workaround comments, issue numbers, known-issue caveats in code or skills).
5. It introduces a known issue affecting a flow this repo drives (build execution, output parsing, dependency reporting, REPL, docs ingestion).
6. It adds, renames, moves, or removes a documentation page that a shipped skill or this skill points to.

Everything else is non-material and needs no row of its own — the sentinel row of its containing unit records that the unit was walked and why nothing in it is material ("Android Studio integration only; no mapped surface" is a typical reason).

Ledger columns:

| # | Source (version + section, or family page + block anchor) | Item | Domain (Table A) | Project-code impact | Shipped-skill impact | Disposition |

Completeness check before leaving step 4: every material item in every walked source has exactly one row, and every walked source unit with zero material items — every top-level section of every release-notes document read, every in-range `changes_T` block across all spanned version-family guides, every crossed-major guide section walked — has exactly one sentinel row.

## Workflows

### 1. Establish the Version Delta

1. Read `gradle/wrapper/gradle-wrapper.properties` and parse NEW from `distributionUrl`; the distribution filename carries the version, so `gradle-9.7.0-bin.zip` parses to NEW = `9.7.0`.
2. Establish OLD from `git log -p gradle/wrapper/gradle-wrapper.properties` (previous `distributionUrl`); fall back to the wrapper version recorded in `.junie/playbook.md`; otherwise ask. Never guess.
3. Classify the delta — this decides steps 2 and 3:
   - **patch-only** — same major.minor, such as 9.6.0 → 9.6.1: one release-notes page; no upgrade-guide blocks in range; no major guide.
   - **within-major** — such as 9.6.1 → 9.7.0: every final release in `(OLD, NEW]`; the in-range `changes_T` blocks of the single spanned version-family guide.
   - **cross-major** — such as 8.14 → 9.6.1: within-major rules applied to every spanned version family (the version-family guide of each major from `major(OLD)` through `major(NEW)` inclusive), plus the major upgrade guide for each crossed major.
4. Confirm the bump is effective: run a trivial task through the repo's normal Gradle entry point and confirm the reported version equals NEW.

### 2. Read the Release Notes (deterministic version walk)

1. **Enumerate the reading set.** Fetch the official versions metadata — `curl.exe -s https://services.gradle.org/versions/all` (JSON array; the same `services.gradle.org` host this repo already uses for `versions/current`). This endpoint is the sanctioned source for release enumeration and supplies version metadata only; all documentation content still comes from `gradle_docs` (Constitution rule 5). Keep only final releases: `snapshot == false`, `nightly == false`, `releaseNightly == false`, `rcFor == ""`, `milestoneFor == ""`, `broken == false`. Sort semantically (major, minor, patch compared numerically) and select every `V` with `OLD < V <= NEW`. Never inject RCs/milestones/nightlies into a final-to-final range; never skip patch releases — patches have their own release-notes pages with patch-specific resolved issues.
2. **Read each selected version.** For each `V` in ascending order: `gradle_docs(path="release-notes.md", version="V")`. Patch pages prepend a patch-specific resolved-issues preamble and then carry the feature-release body — read both parts.
3. **Heading tolerance.** The canonical section list is a template baseline, not an invariant: ingest whatever headings are actually present. Known variation: some releases have no `Promoted features` (such as 9.5.0); documentation children differ (`User Manual` vs `Documentation`/`Training`); `Fixed issues`/`Known issues` may be present but empty (record that). There is no separate docs-changelog page — the `Documentation and training` section is the docs changelog.
4. **Walk each document in this priority order**, flagging material entries for the ledger (definition in Materiality and the Ledger Contract):
   1. `Upgrade instructions` — note which upgrade-guide pages it points to.
   2. `New features and usability improvements` — walk subsections in this repo's priority order: Configuration Cache improvements; Isolated Projects; CLI, logging, and problem reporting; Test reporting and execution; Core plugin and plugin authoring enhancements; Tooling and IDE integration; Build authoring improvements; Dependency management enhancements; Performance improvements; Security and infrastructure; any other present subsections. For each material entry, follow its linked pages: `gradle_docs(path="<linked page>.md", version="V")`, or `gradle_docs(query="tag:javadoc <type or member>", version="V")` for API changes.
   3. `Promoted features` — list every incubating → stable promotion and any removal; these drive lifecycle-language updates in shipped skills.
   4. `Fixed issues` — search repo code and shipped skills for workarounds the fixes obsolete (issue numbers, `workaround` markers, known-issue phrasing; patterns in the impact map).
   5. `Known issues` — flag new caveats for shipped troubleshooting content or repo docs.
   6. `Documentation and training` — note new, renamed, moved, or removed doc pages, then verify every `gradle_docs(path=...)` pointer used in shipped skills still resolves at NEW: `gradle_docs(path=".", version="NEW")` lists the page tree.

### 3. Read the Upgrade Guides (deterministic block selection)

1. **Version-family guides — every spanned major.** For every major `M` with `major(OLD) <= M <= major(NEW)`, read `gradle_docs(path="userguide/upgrading_version_<M>.md", version="NEW")`. Within each family guide, blocks are anchored `#changes_<T>` where `T` is the **target feature release** — the anchor version, NOT the "Upgrading from X and earlier" heading baseline. Include block `changes_T` if and only if `OLD < T <= NEW` (semantic comparison; patch values participate; every block on a family page carries a `T` of that family's major `M`). Never derive the block set from heading text. Extract from each included block: `Potential breaking changes` (must fix) and `Deprecations` (fix the warning before it becomes a break). A same-major delta spans exactly one family guide; a cross-major delta spans one family guide per major in the closed range, and the intermediate majors' guides are mandatory, not optional.
2. **Worked examples.** Within-major: OLD=9.6.1, NEW=9.7.0 spans `upgrading_version_9.md` only; read `changes_9.7.0` only, noting that `changes_9.7.0` is headed "Upgrading from 9.6.0 and earlier" yet still applies because 9.7.0 > 9.6.1. Cross-major: OLD=7.4.0, NEW=9.6.1 spans `upgrading_version_7.md`, `upgrading_version_8.md`, and `upgrading_version_9.md`; include their blocks `changes_T` with 7.4.0 < T <= 9.6.1 — the 7-family page's single `changes_8.0` block (that page is the 7→8 transition at 9.7.0), all `changes_8.x` blocks, and the `changes_9.x` blocks with T <= 9.6.0 (block anchors target feature releases; `9.6.1` is a patch and anchors no block) — and also read the step-3.3 transition guide `upgrading_major_version_9.md` (the 8→9 transition).
3. **Cross-major transition guides.** For each major `N` with `major(OLD) < N <= major(NEW)`, read that major's transition guide — `gradle_docs(path="userguide/upgrading_major_version_<N>.md", version="NEW")` where the page exists (9.7.0: `upgrading_major_version_9.md` for the 8→9 transition); where it does not, the transition content is the `changes_<N>.0` block of the previous family page (the 7→8 transition is `upgrading_version_7.md#changes_8.0`). Read runtime requirements, DSL changes, plugin changes, settings-file changes, task changes, and `Removal of ...` sections. These transition guides are in addition to the per-family within-major blocks of step 3.1, not a replacement for them. Same-major upgrades skip this step entirely.
4. **Patch-only deltas** have no block in range (no feature release `T` satisfies `OLD < T <= NEW`) and span no major boundary — record that and skip this step.
5. Use `gradle_docs(query="tag:upgrading <term>", version="NEW")` for targeted migration detail on a ledger item.
6. Check embedded Kotlin and Groovy version changes across the delta: init scripts and Kotlin DSL build logic compile against them.

### 4. Triage Into the Impact Ledger

Load [Release Notes Impact Map](references/release-notes-impact-map.md). Build the ledger per the Materiality and the Ledger Contract:

1. Tag each material item with exactly one domain from Table A; the map supplies the project-code surfaces and shipped-skill surfaces to open.
2. For project-code impact: open the mapped surfaces (Table C) and look for the affected API, behavior, CLI flag, or output format.
3. For shipped-skill impact: open the mapped skill file/section (Tables A and B) and check its claims, compatibility rows, lifecycle language, and `gradle_docs` pointers against the new docs.
4. Item matches no domain: run the impact map's discovery grep patterns, then decide.
5. Run the completeness check: every material item in every walked source has exactly one ledger row, and every walked source unit with zero material items — every top-level section of every release-notes document read, every in-range `changes_T` block across all spanned version-family guides, every crossed-major guide section walked — has exactly one sentinel row. No code or skill edits happen before the ledger is complete.

### 5. Update Project Code and Tooling

Work ledger items with project-code impact:

1. Fix breaking changes and migrate removed/changed APIs in the Tooling API layer, build execution/event capture, dependency-report parsing, docs services, and init scripts.
2. Resolve deprecations flagged by the in-range upgrade-guide blocks in `build.gradle.kts`, `settings.gradle.kts`, and convention usage.
3. Update tests that assert version-sensitive behavior; add coverage where a ledger item changed behavior this repo relies on.
4. If a change touches an OpenSpec-governed area (build execution, output concurrency, progress reporting, caching/search, multi-reader search), follow the matching spec under `openspec/specs/`.
5. Run targeted tests for changed areas, then `./gradlew test`.

### 6. Update the Shipped Skills

Load [Shipped Skills Update Checklist](references/shipped-skills-update-checklist.md), then work ledger items with shipped-skill impact:

1. Update compatibility quick-reference tables, `Version notes` blocks, and version-sensitive footguns to match NEW. Describe current-state behavior as plain guidance without a version label; use version anchors (`**Gradle N:**` / `as of N`) only for version-specific facts (lifecycle status, deprecations, version numbers, transitional behavior) and past notes. Never use `N+` labels; when a later version is ingested, prior current notes remain as anchored past notes — no `N+` relabelling is ever needed.
2. Apply `Promoted features`: remove incubating/experimental hedges for promoted features; delete or rewrite guidance for removed features.
3. Apply the docs changelog from step 2.4.6: fix or replace `gradle_docs(path=...)` pointers whose pages moved or disappeared; add pointers for new doc areas a skill covers.
4. Add new known-issue caveats to the relevant troubleshooting content; drop caveats made obsolete by fixes.
5. Bump frontmatter `metadata.version` for every skill materially changed; keep any new reference linked from its SKILL.md; respect the `afterEvaluate` safety rule.

### 7. Regenerate the Best-Practices Corpus

Run `./gradlew generateBestPracticesDoc`. The task targets `-PgradleDocsVersion=<v>` when passed, otherwise the running Gradle version (`gradle.gradleVersion`) — NEW once the wrapper bump is in effect. **Regenerates cleanly** means:
1. The task succeeds against NEW. It fails loudly when the new docs structure breaks extraction: the failure message is `No Gradle best-practices pages were extracted for version <v>. Examined entries include: <examined entries>`. If it fails, fix the `:best-practices-generator` module and rerun before proceeding.
2. Review the regenerated files under `build/generated/skills/authoring-gradle-builds/references/best-practices/` (`_index.md` plus per-section files) for material drift caused by the upgrade — sections appearing/disappearing or guidance contradicting the new release notes.
Nothing is committed from this step: the output lives under the gitignored `build/` tree and is regenerated on every build; `zipSkills` merges it into `authoring-gradle-builds/references` inside `skills.zip`. Never hand-edit generated corpus files.

### 8. Verify and Record

1. Run `./gradlew check verifySkillsList` — unit tests (including the skill artifact safety test), `verifyToolsList`, the `integrationTest` and `treeSitterTest` suites (both wired into `check`), and the skills-list sync check. `verifySkillsList` is not wired into `check`, so name it explicitly.
2. If execution-layer code changed, note that `integrationTest` already runs inside `check` (step 8.1); the pre-existing `JavaReplIntegrationTest.initializationError` / `KotlinReplIntegrationTest.basic execution works()` REPL failures are known — name them, don't silently tolerate new ones.
3. Run `./gradlew :updateToolsList` only if Kotlin tool metadata changed.
4. Update the records: the wrapper version line in `.junie/playbook.md`; the `Build Verification` record (date, command, result, context) in `AGENTS.md`.
5. Report: the version delta and its class, the ledger with dispositions, skills changed with version bumps, and verification results.

## Definition of Done

1. Version delta documented with its class (patch-only / within-major / cross-major).
2. Enumeration complete: every final release in `(OLD, NEW]` read; every material item in every walked source has a ledger row, and every walked source unit with zero material items — every top-level section of every release-notes document read, every in-range `changes_T` block across all spanned version-family guides, every crossed-major guide section walked — has exactly one sentinel row.
3. All material items with project-code impact implemented and covered by tests; deprecations from in-range blocks resolved.
4. All material items with shipped-skill impact applied per the shipped-skills checklist; `metadata.version` bumped where material; `SkillArtifactSafetyTest` and `verifySkillsList` pass.
5. Best-practices corpus regenerates cleanly against NEW (task success + drift review; generator fixed if it failed).
6. `./gradlew check` green (or green except explicitly named known pre-existing failures).
7. Records updated: wrapper version line in `.junie/playbook.md`; `Build Verification` record in `AGENTS.md`.

## Reference Discovery

- Load [Release Notes Impact Map](references/release-notes-impact-map.md) at step 4 (triage): release-notes domain → repo code surfaces and shipped-skill surfaces, hotspot inventories with file:line evidence, discovery grep patterns, and lifecycle-language rules.
- Load [Shipped Skills Update Checklist](references/shipped-skills-update-checklist.md) at step 6, and before any edit under `src/main/skills/`: shipping pipeline, edit rules, inventory-change procedure, corpus regeneration, and verification commands.
