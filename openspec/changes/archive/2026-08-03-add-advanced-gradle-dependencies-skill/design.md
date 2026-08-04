# Design: Add Advanced Gradle Dependencies Skill

## Technical Context

The shipped skill portfolio splits dependency work along the operate/author boundary. `using-gradle` covers everyday dependency inspection (`references/dependencies.md`: graph audits, `dependencyInsight` winner analysis, the force/exclude/platform/constraint menu, cache TTL vs `--refresh-dependencies`, conditional verification, update discovery). `authoring-gradle-builds` covers basic dependency authoring (`references/dependencies-and-catalogs.md`, `dependency-locking.md`, `configurations-and-variants.md`: declarations, version-catalog basics, repositories/content filters, constraints/BOMs, locking basics, custom-attribute/feature-variant basics). A Gradle 9.x documentation survey identified a deep advanced-dependency portfolio owned by neither skill, whose real workflows are diagnose→fix loops crossing that split. This change adds a dedicated fifth skill to own that cross-cutting lane, additively.

Binding repo constraints that shape the design:

- **Registration**: `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt` holds the human-facing portfolio descriptions in the `DESCRIPTIONS` linked map (portfolio order); `discoverSkills` enforces that each `SKILL.md` frontmatter `name:` equals its directory name; agent-facing triggers live in the frontmatter `description: |` block. `:updateSkillsList` / `:verifySkillsList` splice/verify `docs/skills.md` between `SKILLS_LIST_START`/`SKILLS_LIST_END` markers; `zipSkills` packages `src/main/skills` into `skills.zip`, extracted by `install_gradle_skills` in `SkillTools.kt`.
- **Frontmatter convention** (per `using-gradle`/`authoring-gradle-builds`): `description: |` block with `## Positive Triggers (when to activate)` and `## Negative Triggers (when NOT to activate)` bullets; `license: Apache-2.0`; `metadata: author: https://github.com/rnett/gradle-mcp`; a new skill starts at `version: "1.0.0"`.
- **Binding skill specs**: `skill-metadata` (negative triggers mandatory for routing-ambiguous skills — this skill is maximally routing-ambiguous against two neighbors), `skill-doc-link-convention` (all documentation links are canonical `gradle_docs(path=...)` / `query="tag:..."` hints; coverage by human review), `skill-reference-discoverability` (every reference reachable from the SKILL.md body), `skill-and-tool-descriptions` (gerund-open descriptions).
- **Test guardrails**: `SkillToolsTest` (inventory assertions, keyed on top-level skill directories), `UpdateSkillsTest` (docs splice sync), `SkillArtifactSafetyTest` (every `afterEvaluate` mention in any skill markdown must be in a prohibition context).

Scope boundaries: no runtime code or MCP tool changes; no content moves out of existing skills (the single retained-reference routing-alignment edit in D10 replaces an enablement direction in place and moves nothing); the frozen `references/best-practices/` corpus stays byte-identical; `interacting-with-project-runtime` and `verifying-compose-ui` are untouched.

## Decisions

### D1: Dedicated fifth skill (settled)

A dedicated fifth shipped skill owns all advanced dependency depth. Alternatives — expanding `using-gradle`/`authoring-gradle-builds` in place, or splitting the advanced topics across both — were rejected in prior review: expansion breaks the compact-body budget and the operate/author identity of both skills, while splitting reproduces the exact cross-cutting problem this change solves. The new skill is additive; existing skills keep today's basics, and their only changes are handoff routing rows and frontmatter negative-trigger bullets in their `SKILL.md` files plus the single D10 routing-alignment edit in `authoring-gradle-builds/references/dependencies-and-catalogs.md`.

### D2: Boundary and handoff matrix (settled)

| Topic | Home |
| :--- | :--- |
| Graph audits, `dependencyInsight` winner analysis, force/exclude/platform/constraint menu, TTL vs `--refresh-dependencies`, update discovery, trivial dependency edits | `using-gradle` (unchanged) |
| Dependency declarations, basic version catalogs (everyday catalog entries and library declarations), repository/content-filter wiring, constraints/BOMs, locking basics, custom-attribute/feature-variant basics | `authoring-gradle-builds` (unchanged) |
| Variant-aware resolution diagnostics (attributes, compatibility vs disambiguation rules, `outgoingVariants`, `dependencyInsight --all-variants`) | `advanced-gradle-dependencies` |
| Dependency verification (metadata structure, PGP keys, checksums, CI workflows) | `advanced-gradle-dependencies` |
| Component metadata rules and selection rules | `advanced-gradle-dependencies` |
| Dependency substitution and composite builds | `advanced-gradle-dependencies` |
| Feature variants and configuration roles, capability conflicts | `advanced-gradle-dependencies` |
| Locking lock modes (deep dive beyond basics) | `advanced-gradle-dependencies` |
| Advanced version catalog topics | `advanced-gradle-dependencies` |
| Repository governance modes (`dependencyResolutionManagement`, content filtering, `exclusiveContent`) | `advanced-gradle-dependencies` |
| Caching/freshness, resolution consistency, resolution-avoidance/performance | `advanced-gradle-dependencies` (`resolution-mechanics.md`, see D3) |

Handoffs are expressed as `## Cross-Skill Handoffs` rows in all three SKILL.md files plus one frontmatter negative-trigger bullet in each existing skill (negative triggers are mandatory for routing-ambiguous skills per `skill-metadata`).

### D3: Phase split and cross-cutting placement (settled split; placement judgment recorded)

Delivery is phased: **Phase 1** is the diagnostics/safety core (variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution/composites) — the diagnose-first half of the loop and the safety-critical material. **Phase 2** is governance and advanced authoring (feature variants/capabilities, lock modes, advanced catalogs, repository governance).

Placement judgment: the three cross-cutting surveyed topics — caching/freshness, resolution consistency, and performance/resolution-avoidance — are consolidated into a single Phase-2 reference, `resolution-mechanics.md`. Rationale: all three are resolution-engine mechanics rather than independent authoring domains; they exist to support governance decisions (what a lock mode, governance mode, or substitution buys you at resolution time), so they belong with Phase-2 governance authoring rather than Phase-1 diagnostics. Consolidating them into one reference avoids three thin files and keeps the Phase-2 reference count at five.

### D4: SKILL.md structure per sibling convention

The new `SKILL.md` mirrors the sibling layout: frontmatter (`name`, gerund-open `description: |` with Positive/Negative Triggers, `license`, `metadata`), then body sections — Constitution (including the `gradle` tool-first rule, wrapper-first version scoping, and diagnose-before-fix), Decision Routing, Reference Discovery, Cross-Skill Handoffs, and Workflows. Decision Routing/Reference Discovery rows exist only for references that already ship in the current phase (see D8).

### D5: Literal registration copy (verbatim)

Frontmatter `description:` (gerund-open, per `skill-and-tool-descriptions`):

> Diagnoses and fixes advanced Gradle dependency resolution problems across the operate/author split: variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution and composite builds, and dependency governance.

`## Positive Triggers (when to activate)` bullets:

- Variant selection failures or attribute mismatches, diagnosed via `outgoingVariants` and `dependencyInsight --all-variants`
- Dependency verification metadata, PGP keys, and CI verification workflows
- Component metadata rules, selection rules, dependency substitution, and composite build diagnosis
- Capability conflicts, feature variants, lock modes, advanced version catalogs beyond everyday catalog entries, repository governance, and caching/freshness tuning

`## Negative Triggers (when NOT to activate)` bullets:

- Everyday dependency inspection, conflicts, or updates → `using-gradle`
- Dependency declarations, basic version catalogs, or basic locking → `authoring-gradle-builds`
- Running builds or generic failure diagnosis → `using-gradle`
- Non-dependency structural authoring → `authoring-gradle-builds`

`UpdateSkills.kt` `DESCRIPTIONS` entry, inserted after `authoring-gradle-builds`:

> Owns advanced Gradle dependency engineering across the operate/author split as diagnose→fix loops: variant-aware resolution diagnostics (attributes, compatibility vs disambiguation rules, outgoingVariants, dependencyInsight --all-variants), dependency verification (verification-metadata.xml structure, PGP keys, CI workflows), component metadata rules and selection rules, dependency substitution and composite builds, and dependency governance (feature variants and configuration roles, capability conflicts, lock modes, advanced version catalogs, repository governance modes, and resolution caching/consistency/avoidance). Everyday dependency inspection and trivial edits stay in using-gradle; basic dependency declaration, version-catalog basics, and locking stay in authoring-gradle-builds.

Handoff rows:

- `using-gradle/SKILL.md` `## Cross-Skill Handoffs` adds: **Advanced Dependency Engineering** (variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution/composite builds, dependency governance) → `advanced-gradle-dependencies`; plus one frontmatter negative-trigger bullet.
- `authoring-gradle-builds/SKILL.md` `## Cross-Skill Handoffs` adds the mirror row — advanced dependency engineering routes out; basic dependency declaration, version-catalog basics, and locking stay here — plus one frontmatter negative-trigger bullet.

### D6: Test and verification impact (including why `:updateToolsList` is not needed)

- `SkillToolsTest`: `expectedInventory` moves to the five-name set; the inline inventory list and the zip-content test ("skills zip contains exactly the four-name inventory") move to five names; new-skill reference assertions follow the phase discipline — Phase 1 asserts only the four Phase-1 references so Phase-1 verification passes at the phase boundary, and Phase 2 extends the assertions to the five Phase-2 references (all nine) before its final test run.
- `UpdateSkillsTest`: passes once `:updateSkillsList` re-splices `docs/skills.md` against the new `DESCRIPTIONS`.
- `SkillArtifactSafetyTest`: passes with no changes — new content must simply keep every `afterEvaluate` mention in a prohibition context.
- `:verifySkillsList` runs explicitly after registration (not part of `check` — wiring it in is an excluded follow-up).
- **`:updateToolsList` is NOT required**: tool descriptions and MCP tool metadata are unchanged by this change, and `docs/tools/SKILL_TOOLS.md` does not enumerate shipped skills; only skill metadata changes, which is `:updateSkillsList` territory.

### D7: Delta selection

- **NEW** `advanced-gradle-dependencies` capability — identity/boundary, phased coverage, workflow, handoff contract, discoverability, registration/packaging, documentation routing, verification doctrine.
- **MODIFIED** `skill-infrastructure` — "Package and Install Guardrails" is the only requirement that hardcodes the portfolio cardinality ("four-name portfolio inventory"), so it is restated as the five-name inventory with all scenarios updated; no other `skill-infrastructure` requirement changes.
- **ADDED** "Advanced Dependency Depth Handoff" to `using-gradle` and `authoring-gradle-builds`. These are ADDED requirements: the existing basics requirements remain exactly as written, and the handoff is new behavior layered on top.
- **MODIFIED** `authoring-gradle-builds` "Dependency Verification Doctrine" — the one existing requirement whose scenario conflicts with the new skill's verification ownership: its "Implement dependency verification" scenario currently directs verification enablement inside `authoring-gradle-builds`, which would leave identical verification-authoring requests two valid destinations. The restatement keeps the conditional-only doctrine and the UX-cost warning, and routes verification implementation (metadata authoring, PGP key and checksum workflows, repair, CI workflows) to `advanced-gradle-dependencies` so all three skill contracts agree on the destination. The `using-gradle` main spec has no equivalent implementation-directing requirement, so it needs no MODIFIED delta; the retained conditional verification cautions stay in both existing skills (no content moves). D10 traces the same ownership into the retained shipped reference that the authoring skill loads for dependency work.

### D8: Two-stage delivery inside one change

Both phases ship under this single change, but tasks.md sequences them: Phase 1 creates the SKILL.md skeleton with Decision Routing/Reference Discovery rows for the four Phase-1 references only; Phase 2 adds the five references together with their routing rows. This guarantees no dead reference links at the phase boundary — every routing row points at a file that already exists when the phase lands, test reference assertions follow the same discipline (Phase 1 asserts only the four Phase-1 references; Phase 2 extends the assertions to all nine before its final test run), and registration/tests are verified after each phase.

### D9: Content doctrine

- **Documentation links**: per `skill-doc-link-convention`, every documentation citation in the new skill is a canonical `gradle_docs(path=...)` or `query="tag:..."` hint; no published `docs.gradle.org` URLs, no fabricated tool names. Link coverage is enforced by human review (no automated gate exists).
- **Safety**: per `SkillArtifactSafetyTest`, any `afterEvaluate` mention in the new skill's markdown must appear in a prohibition context.
- **Version notes**: wrapper-first — version-sensitive advice is scoped to the wrapper version read before use, biased to the latest supported Gradle with documented fallbacks.
- **Verification doctrine**: dependency verification stays conditional-only, consistent with the portfolio-wide doctrine corrected by the 2026-08-03 modernization change.

### D10: Retained-reference routing alignment (dependency verification)

`authoring-gradle-builds/SKILL.md`'s Add Dependency workflow declares `references/dependencies-and-catalogs.md` the single authoritative dependency procedure, and that reference's `## Dependency verification and supply chain` Decision rule currently ends "…then enable it deliberately" — an in-skill enablement direction. Once the Advanced Dependency Depth Handoff and the MODIFIED "Dependency Verification Doctrine" route verification implementation to `advanced-gradle-dependencies`, the shipped authoring contract would otherwise carry two contradictory authoritative instructions: the handoff sends verification implementation out while the loaded reference directs enablement in-skill. The implementation therefore replaces that single enablement direction with a handoff pointer that routes verification implementation (`verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, CI verification workflows) to `advanced-gradle-dependencies`, keeping the conditional-only framing, the UX-cost list, the locking-vs-verification distinction, and the `--dependency-verification=off` caution exactly in place.

This is a routing-alignment edit, not a content move: no doctrine or basics relocate out of `authoring-gradle-builds` — the conditional doctrine, UX-cost warning, and basic cautions stay where they are, and only the destination of the implementation direction changes. The settled decision ("additive — no content moves out of existing skills") prohibits relocating or deleting retained content; it does not require a retained reference to keep directing work that the contract now routes elsewhere.

Sibling-instruction check (verified against the shipped files, not assumed): no other retained authoring instruction directs in-skill implementation of a routed advanced topic. `dependency-locking.md` keeps locking basics and contains no lock-mode content; no substitution or composite-build implementation direction exists (`convention-plugins.md` composite usage is the retained build-logic pattern, a different topic; `dependencies-and-catalogs.md` mentions substitution only as a resolution rule that can change the winner); `configurations-and-variants.md` retains only declarative feature-variant and capability basics with no conflict-resolution procedure; the `FAIL_ON_PROJECT_REPOS` one-liner is part of the retained repository-authoring basics, not a governance-mode procedure; and the caching/freshness section already hands execution to `using-gradle`. The only other dependency-verification mentions (`dependency-locking.md`'s locking-vs-verification distinction and its "a lockfile is not proof of trustworthiness" caution) are retained doctrine, not implementation directions. Exactly one bounded edit is required.

## Verification Plan

- **Structure**: `openspec validate add-advanced-gradle-dependencies-skill --strict`.
- **Inventory**: `./gradlew :test --tests "dev.rnett.gradle.mcp.tools.skills.SkillToolsTest"` — five-name inventory, zip contents, new-skill references.
- **Docs splice sync**: `./gradlew :test --tests "dev.rnett.gradle.mcp.UpdateSkillsTest"`.
- **Safety audit**: `./gradlew :test --tests "dev.rnett.gradle.mcp.skills.SkillArtifactSafetyTest"`.
- **Metadata**: explicit `./gradlew :verifySkillsList` (not part of `check`).
- **Human review**: doc-link convention audit (all citations are `gradle_docs` hints) — the reference-reachability and link-convention gate, per repo doctrine.

## Risks / Trade-offs

- **Routing ambiguity**: three skills now touch dependencies; mis-routing is the primary risk. Mitigation: mandatory negative triggers in all three frontmatters (per `skill-metadata`), explicit boundary table (D2), and handoff rows pointing both directions.
- **Content overlap with existing references**: basics exist in `using-gradle`/`authoring-gradle-builds` and advanced depth references the same mechanisms. Mitigation: the D2 boundary table is normative; advanced references link back to basics rather than restating them; no content moves; the D10 alignment edit removes the one retained instruction that would direct routed work back in-skill.
- **Over-specification**: nine references raise cognitive load. Mitigation: progressive disclosure — compact SKILL.md body, task-shaped references loaded on demand via routing rows.
- **Phase-boundary dead links**: mitigated by D8 — routing rows only ever point at already-created references.

## Open Questions

None. All decisions were settled in prior review rounds; the only judgments made here (D3 cross-cutting placement, D7 delta form, D10 retained-reference alignment) are recorded above with rationale.
