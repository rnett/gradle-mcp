## Context

The server ships five skills from `src/main/skills`. Their frontmatter descriptions currently include full Positive and Negative Triggers sections, so discovery metadata duplicates body-level routing guidance and can exceed client metadata budgets. Claude Code truncates `description` and `when_to_use` after 1,536 characters, while the repository currently validates only a skill's `name`.

## Goals / Non-Goals

**Goals:**

- Keep every parsed frontmatter description non-empty and at or below 400 Kotlin `String.length` units.
- Keep parsed `description` plus optional `when_to_use` at or below 1,536 Kotlin `String.length` units.
- Preserve detailed routing guidance in the body and a primary negative boundary in discovery metadata.
- Make malformed metadata, budget drift, manifest drift, and generated-document drift fail unit or verification checks.

**Non-Goals:**

- Changing the shipped skill inventory or reference files.
- Changing tool metadata or wiring skill verification into `check` as a separate task dependency.
- Archiving this change or synchronizing its deltas into main specs.

## Decisions

### Decision: Use compact frontmatter and body-level trigger inventories

Each description is one or two sentences containing capability anchors, an activation boundary, and the primary negative routing boundary. The existing Positive and Negative Triggers sections move immediately after the body overview, before detailed workflows, preserving their routing meaning while avoiding discovery-time duplication.

### Decision: Count parsed scalar values

Validation parses YAML frontmatter scalar forms deterministically and counts Kotlin `String.length`, which measures UTF-16 code units. The description limit is 400 characters. If `when_to_use` exists, its parsed length is added to the parsed description length and the combined limit is 1,536; absence contributes zero.

### Decision: Share parser and validation logic with tests

`UpdateSkills.verify()` uses reusable frontmatter parsing and description-validation functions. Parsing errors are reported as violations instead of being skipped, and budget diagnostics name the skill, measured length, and applicable limit. Unit tests call the same logic for exact boundaries and validate shipped files discovered through the production inventory path.

### Decision: Keep agent-facing and human-facing summaries identical

`UpdateSkills.DESCRIPTIONS` contains the exact frontmatter descriptions in canonical portfolio order. Generated `docs/skills.md` uses that map, and tests compare every parsed shipped description with its matching map value.

## Risks / Trade-offs

- A small parser supports the scalar forms used by skill metadata without adding a YAML dependency; unsupported or malformed target metadata fails closed with an actionable diagnostic.
- Moving trigger sections increases body content near the overview, but that detail is loaded only after activation and preserves established routing guidance.
