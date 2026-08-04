# Proposal: Add Advanced Gradle Dependencies Skill

## Why

The shipped Gradle skill portfolio splits dependency work along the operate/author boundary: `using-gradle` owns everyday dependency inspection (graph audits, `dependencyInsight` winner analysis, the force/exclude/platform/constraint menu, cache TTL vs `--refresh-dependencies`, update discovery), and `authoring-gradle-builds` owns basic dependency authoring (declarations, version-catalog basics, repositories and content filters, constraints/BOMs, locking basics). A survey against the Gradle 9.x documentation inventory identified a deep portfolio of advanced dependency engineering that neither skill owns: variant-aware resolution diagnostics, dependency verification, component metadata rules, dependency substitution and composite builds, feature variants and capability conflicts, lock modes, advanced version catalogs, repository governance modes, and resolution caching/consistency/avoidance mechanics.

The correct real-world workflows for these topics are diagnose→fix loops that cross the operate/author split: diagnose a variant-selection failure (operate), author an attribute compatibility rule (author), re-diagnose to confirm (operate). Today that cross-cutting lane has no home — agents bounce between two skills that each stop at the basics. This change adds a dedicated fifth shipped skill, `advanced-gradle-dependencies`, to own that lane.

## What Changes

### New skill: `advanced-gradle-dependencies` (additive)

- Create `src/main/skills/advanced-gradle-dependencies/` with a `SKILL.md` following sibling conventions (frontmatter description with positive/negative triggers, Constitution, Decision Routing, Reference Discovery, Cross-Skill Handoffs, Workflows) and a `references/` directory.
- **Phase 1 — diagnostics/safety core** references:
  - `variant-resolution-diagnostics.md` — attributes, compatibility vs disambiguation rules, the `outgoingVariants` report, `dependencyInsight --all-variants`.
  - `dependency-verification.md` — `verification-metadata.xml` structure, PGP keys, checksums, CI workflows; conditional-only doctrine consistent with the existing skills.
  - `component-metadata-rules.md` — component metadata rules and selection rules.
  - `substitution-and-composites.md` — dependency substitution and composite builds.
- **Phase 2 — governance/advanced authoring** references:
  - `feature-variants-and-capabilities.md` — feature variants, configuration roles, capability conflicts.
  - `dependency-locking-deep-dive.md` — lock modes and deep locking behavior.
  - `advanced-version-catalogs.md` — advanced version catalog topics.
  - `repository-governance.md` — `dependencyResolutionManagement` modes, content filtering, `exclusiveContent`.
  - `resolution-mechanics.md` — consolidated caching/freshness, resolution consistency, and performance/resolution-avoidance mechanics (placement rationale in design.md, D3).
- Register the skill in `UpdateSkills.kt` `DESCRIPTIONS` (inserted after `authoring-gradle-builds`) and re-splice `docs/skills.md` via `:updateSkillsList`.

### Handoff wiring (additive; no content moves out of existing skills)

- `using-gradle/SKILL.md`: add one `## Cross-Skill Handoffs` row ("Advanced Dependency Engineering") and one frontmatter negative-trigger bullet; its existing `references/dependencies.md` basics stay untouched.
- `authoring-gradle-builds/SKILL.md`: mirror handoff row (advanced dependency engineering routes out, including verification metadata/key/checksum/repair/CI implementation; basic dependency declaration, version-catalog basics, and locking stay here) plus a frontmatter negative-trigger bullet. Existing reference basics stay in place; the only reference edit is a bounded routing-alignment fix in `references/dependencies-and-catalogs.md`, which replaces the dependency-verification enablement direction with a handoff pointer while keeping the conditional doctrine, UX-cost warning, locking-vs-verification distinction, and disable caution (design.md D10).

### Portfolio guardrail

- The `skill-infrastructure` "Package and Install Guardrails" requirement moves from the four-name to the five-name portfolio inventory; `SkillToolsTest` inventory assertions move to five names.

## Capabilities

### New Capabilities

- `advanced-gradle-dependencies`: A dedicated fifth skill owning advanced dependency engineering across the operate/author split as diagnose→fix loops — variant-aware resolution diagnostics, dependency verification, component metadata and selection rules, substitution and composite builds, and dependency governance (feature variants and configuration roles, capability conflicts, lock modes, advanced version catalogs, repository governance modes, and resolution caching/consistency/avoidance mechanics).

### Modified Capabilities

- `skill-infrastructure`: "Package and Install Guardrails" now asserts the five-name portfolio inventory.
- `using-gradle`: ADDED "Advanced Dependency Depth Handoff" requirement — everyday dependency basics retained, advanced dependency depth routes to the new skill.
- `authoring-gradle-builds`: ADDED "Advanced Dependency Depth Handoff" requirement — basic declaration/version-catalog/locking authoring and the conditional-only verification doctrine retained, advanced dependency depth (including verification metadata/key/checksum/repair/CI implementation) routes to the new skill. MODIFIED "Dependency Verification Doctrine" — conditional doctrine retained, verification implementation routes to the new skill so all three skill contracts agree on the destination, and the modified ownership is traced into the retained `dependencies-and-catalogs.md` procedure (design.md D10).

## Impact

- **New**: `src/main/skills/advanced-gradle-dependencies/` — `SKILL.md` plus nine references across the two phases.
- **Modified**:
  - `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt` — new `DESCRIPTIONS` entry (after `authoring-gradle-builds`).
  - `docs/skills.md` — re-spliced by `./gradlew :updateSkillsList`.
  - `src/main/skills/using-gradle/SKILL.md` and `src/main/skills/authoring-gradle-builds/SKILL.md` — handoff rows and frontmatter negative-trigger bullets only.
  - `src/main/skills/authoring-gradle-builds/references/dependencies-and-catalogs.md` — the dependency-verification enablement direction becomes a handoff pointer; the conditional doctrine, UX costs, and cautions stay in place (design.md D10).
  - `dev.rnett.gradle.mcp.tools.skills.SkillToolsTest` — five-name inventory and new-skill assertions.
- **Unchanged**: no runtime code or MCP tool behavior changes; `./gradlew :updateToolsList` is NOT required because no tool descriptions change (design.md, D6); the frozen `references/best-practices/` corpus stays byte-identical; `interacting-with-project-runtime` and `verifying-compose-ui` are untouched.

## Deferred Follow-ups

1. **Wire `:verifySkillsList` into `check`.** Explicitly ruled out of scope for this change; the task stays explicit (`./gradlew :verifySkillsList`). Adding it to `check` is follow-up work.
