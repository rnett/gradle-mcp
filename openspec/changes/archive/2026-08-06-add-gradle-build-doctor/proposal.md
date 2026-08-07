# Proposal: Add Gradle Build Doctor to Authoring Skill

## Why

Agents authoring or modifying Gradle builds have no structured way to assess build health — wrapper/version posture, build-logic hygiene, and exposure to best-practice and deprecation drift — before or while editing. `authoring-gradle-builds` currently offers only the narrow `### Performance Audit` workflow, and there is no workflow that assesses the build against the skill's own knowledge and proposes prioritized improvements.

This change relies on the skill's knowledge rather than precisely specified goals or contracts: the assessment reads the build's files and **reads and applies the skill's embedded best-practice docs** — `references/best-practices/_index.md` plus area pages — as its PRIMARY source, together with this skill's `SKILL.md` and its references (including `references/upgrading-and-release-notes.md` for migration guides and release notes) as SECONDARY, and `gradle_docs` as the authoritative/current SUPPLEMENT, with a small set of cheap assessment commands run as ordinary workflow steps only where static reading cannot produce the evidence. The knowledge hierarchy is defined in exactly one place (the reference's `### Knowledge sources`) and deferred to via `#knowledge-sources` links everywhere else.

## What Changes

- **Embed a Build Health Assessment (Doctor) workflow** in `authoring-gradle-builds` as `### Build Health Assessment (Doctor)`, replacing `### Performance Audit` in place (same `## Workflows` slot, trigger retained, no second performance workflow).
- **Exactly one reference** — `references/build-health-assessment.md` (fixed name; no alternative, no two-file split) carrying both the procedure and the report material, specified at section level: frontmatter/purpose, orientation file list, the single authoritative `### Knowledge sources` hierarchy (PRIMARY/SECONDARY/SUPPLEMENT), knowledge-driven assessment, minimal probes, findings/report/remediation, and scope boundaries. No authored check catalog; zero packaging/implementation vocabulary about the corpus.
- **Knowledge-driven, static-first assessment that reads and applies the skill's embedded best-practice docs** as PRIMARY, with `gradle_docs` as the authoritative/current SUPPLEMENT and this skill's references (including `references/upgrading-and-release-notes.md`) as SECONDARY; the agent's Gradle/build knowledge determines what applies.
- **Minimal probes as ordinary steps**: only where static reading cannot produce the evidence, the workflow may run a small set of cheap assessment commands using existing MCP tools — `gradle help --warning-mode all` / `tasks --warning-mode all` (deprecation warnings), `gradle --version` (wrapper version), `gradle help --configuration-cache` (configuration-cache compatibility), `inspect_dependencies` (version-health/plugin-posture support), `query_build` (last-known build problems). Probes surface observable signals only; nothing verifies build task outputs or artifacts; no execution barriers, no probe contracts.
- **Advisory, summary-first reporting**: the report format is defined first (Report Template) with examples after; findings carry type/area/severity/evidence (`direct` / `observed` / `web`)/recommendation with a doctrine pointer; classes A. Build Script Errors → B. Forward-Compat & Risks → C. Recommendations → D. Healthy Areas; severity counts and prioritized recommendations (0–5, not manufactured) with per-class totals; forward-compat severity `high` only if removal lands in the next Gradle major (labeled e.g. `Future Breakage (Gradle 10)`); corpus-freshness note when applicable.
- **Propose-only remediation**: findings are proposals; edits are applied only after explicit user approval via this skill's normal authoring workflows.
- **Minimal SKILL.md edits**: frontmatter Positive Triggers reconciled (assessment phrases, retaining the performance trigger); one Decision Routing row for health assessment; `### Performance Audit` replaced in place by `### Build Health Assessment (Doctor)` as a lean pointer to the reference (knowledge hierarchy deferred via the `#knowledge-sources` link); `metadata.version` bumped `1.4.0 → 1.5.0`.
- **No new MCP tools**, no edits to other skills, to the embedded corpus, or to the tooling that produces it (the doctor reads the embedded corpus, never edits it).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `authoring-gradle-builds`: exactly one capability delta. `Requirement: Modification Index` is updated as a complete replacement retaining all of its own base text and existing scenarios verbatim, with minimal doctor additions (the Build Health Assessment workflow is available as `### Build Health Assessment (Doctor)`, replaces `### Performance Audit` in place, and is bound to exactly one reference, `references/build-health-assessment.md`). Added requirements encode the knowledge-driven workflow (embedded best-practices corpus as PRIMARY read/apply source; knowledge hierarchy defined in exactly one place — the reference's `### Knowledge sources`), the advisory summary-first findings/report model, and propose-only consented remediation.

## Impact

- `src/main/skills/authoring-gradle-builds/SKILL.md` — four minimal edits: frontmatter Positive Triggers, one Decision Routing row, `### Build Health Assessment (Doctor)` replacing `### Performance Audit` in place (lean pointer to the reference), version bump to `1.5.0`.
- `src/main/skills/authoring-gradle-builds/references/build-health-assessment.md` — new, exactly one file (included by `zipSkills` automatically; no build-script change).
- A lightweight content-gate test under `src/test/kotlin/dev/rnett/gradle/mcp/skills/` (minimal sanity assertions; see design).
- No changes to `using-gradle`, other skills, the MCP server surface, or the embedded corpus.

This proposal documents the final workflow shape: knowledge-driven (no authored check catalog), static-first, one reference, one capability delta, propose-only remediation. Implementation is complete and this change is archived with its delta specs synced into the main specs.
