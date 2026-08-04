# Tasks: Add Advanced Gradle Dependencies Skill

Every checklist item names the concrete file it changes. Phases follow design.md D8: registration and tests are verified after each phase, and Decision Routing rows only ever point at references that already exist.

## Implementation Checklist

### 1. Phase 1 — skill skeleton and diagnostics/safety references

- [x] Create `src/main/skills/advanced-gradle-dependencies/SKILL.md`: frontmatter per sibling convention (`name: advanced-gradle-dependencies`; gerund-open `description: |` block with `## Positive Triggers (when to activate)` and `## Negative Triggers (when NOT to activate)` bullets exactly as in design.md D5; `license: Apache-2.0`; `metadata: author: https://github.com/rnett/gradle-mcp`, `version: "1.0.0"`). Body: Constitution (Gradle MCP `gradle` tool-first, wrapper-first version scoping, diagnose-before-fix), Decision Routing and Reference Discovery rows for the four Phase-1 references only, Cross-Skill Handoffs (bidirectional rows with `using-gradle` and `authoring-gradle-builds`), and Workflows structured as diagnose→fix loops.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/variant-resolution-diagnostics.md`: attributes, compatibility rules vs disambiguation rules, the `outgoingVariants` report, `dependencyInsight --all-variants`; diagnose→fix loop; `gradle_docs` hints only.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/dependency-verification.md`: `verification-metadata.xml` structure, PGP key handling, checksums, CI workflows; conditional-only doctrine with honest UX-cost reporting; locking-vs-verification distinction.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/component-metadata-rules.md`: component metadata rules and dependency selection rules.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/substitution-and-composites.md`: dependency substitution and composite build diagnosis and authoring.

### 2. Registration, handoff wiring, and guardrail updates

- [x] `src/main/kotlin/dev/rnett/gradle/mcp/UpdateSkills.kt`: add the `DESCRIPTIONS` entry for `advanced-gradle-dependencies` (verbatim text in design.md D5), inserted after the `authoring-gradle-builds` entry to preserve portfolio order.
- [x] `src/main/skills/using-gradle/SKILL.md`: add the `## Cross-Skill Handoffs` row "**Advanced Dependency Engineering** (variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution/composite builds, dependency governance) → `advanced-gradle-dependencies`" and one frontmatter negative-trigger bullet for advanced dependency work.
- [x] `src/main/skills/authoring-gradle-builds/SKILL.md`: add the mirror `## Cross-Skill Handoffs` row (advanced dependency engineering routes out, including verification metadata/key/checksum/repair/CI implementation; basic dependency declaration, version-catalog basics, and locking stay here) and one frontmatter negative-trigger bullet.
- [x] `src/main/skills/authoring-gradle-builds/references/dependencies-and-catalogs.md` (design.md D10): in `## Dependency verification and supply chain`, replace only the Decision rule's enablement direction ("…then enable it deliberately") with a handoff pointer that routes verification implementation (`verification-metadata.xml` authoring, PGP key and checksum workflows, verification repair, CI verification workflows) to `advanced-gradle-dependencies`; keep the conditional-only framing, the UX-cost list, the locking-vs-verification distinction, and the `--dependency-verification=off` caution in place. No other content in the reference changes.
- [x] `src/test/kotlin/dev/rnett/gradle/mcp/tools/skills/SkillToolsTest.kt`: move `expectedInventory` to the five-name set, update the inline inventory list, update the zip-content test to the five-name inventory, and add reference assertions for the four Phase-1 references only (`variant-resolution-diagnostics.md`, `dependency-verification.md`, `component-metadata-rules.md`, `substitution-and-composites.md`) so the Phase-1 test run passes at the phase boundary; Phase-2 reference assertions are added in section 3.
- [x] Run `./gradlew :updateSkillsList` to re-splice `docs/skills.md` between the `SKILLS_LIST_START`/`SKILLS_LIST_END` markers.
- [x] Run `./gradlew :test --tests "dev.rnett.gradle.mcp.tools.skills.SkillToolsTest"`, `./gradlew :test --tests "dev.rnett.gradle.mcp.UpdateSkillsTest"`, and `./gradlew :test --tests "dev.rnett.gradle.mcp.skills.SkillArtifactSafetyTest"`.
- [x] Run `./gradlew :verifySkillsList` (explicit task; not part of `check`).

### 3. Phase 2 — governance and advanced-authoring references

- [x] Create `src/main/skills/advanced-gradle-dependencies/references/feature-variants-and-capabilities.md`: feature variants, configuration roles, capability conflict resolution.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/dependency-locking-deep-dive.md`: lock modes and deep locking behavior beyond the `authoring-gradle-builds` basics.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/advanced-version-catalogs.md`: advanced version catalog topics.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/repository-governance.md`: `dependencyResolutionManagement` modes, content filtering, `exclusiveContent`.
- [x] Create `src/main/skills/advanced-gradle-dependencies/references/resolution-mechanics.md`: consolidated caching/freshness, resolution consistency, and performance/resolution-avoidance mechanics (placement rationale: design.md D3).
- [x] `src/main/skills/advanced-gradle-dependencies/SKILL.md`: add Decision Routing and Reference Discovery rows for the five Phase-2 references.
- [x] `src/test/kotlin/dev/rnett/gradle/mcp/tools/skills/SkillToolsTest.kt`: extend the new skill's reference assertions to the five Phase-2 references (`feature-variants-and-capabilities.md`, `dependency-locking-deep-dive.md`, `advanced-version-catalogs.md`, `repository-governance.md`, `resolution-mechanics.md`) so the packaged-artifact assertions cover all nine references before the final test run.
- [x] Re-run the Phase-2 verification set: the three test classes above plus `./gradlew :verifySkillsList`.

### 4. Spec and documentation verification

- [x] Run `openspec validate add-advanced-gradle-dependencies-skill --strict`.
- [x] Human review (the doc-link gate per `skill-doc-link-convention`): every documentation citation in the new skill is a canonical `gradle_docs(path=...)` or `query="tag:..."` hint — no published `docs.gradle.org` URLs, no fabricated tool names; every reference is reachable from the SKILL.md body.

## Explicit Exclusions

- **Wiring `:verifySkillsList` into `check`**: explicitly ruled out of scope; the task remains explicit. Recorded as a deferred follow-up in proposal.md.
- **`./gradlew :updateToolsList`**: NOT required — no MCP tool descriptions change, and `docs/tools/SKILL_TOOLS.md` does not enumerate shipped skills (rationale: design.md D6).
- **Frozen best-practices corpus**: untouched and byte-identical.
- **No content moves**: `using-gradle` and `authoring-gradle-builds` keep all existing reference content — no doctrine or basics relocate out. The sole exception is a bounded routing-alignment edit, not a content move: `authoring-gradle-builds/references/dependencies-and-catalogs.md` replaces its verification enablement direction with a handoff pointer so the retained reference does not contradict the new ownership (design.md D10).
