## Why

Shipped skill descriptions have grown into trigger inventories that are loaded before a skill is selected. Some exceed Claude Code's 1,536-character combined budget for `description` and `when_to_use`, which risks truncating routing metadata and wastes context on guidance already available in the skill body.

## What Changes

- Replace each shipped skill's frontmatter description with a compact, discovery-focused description of no more than 400 parsed characters.
- Move detailed Positive and Negative Triggers sections into each `SKILL.md` body while retaining the primary routing boundary in frontmatter.
- Validate parsed frontmatter and enforce both the 400-character description budget and 1,536-character combined `description` plus optional `when_to_use` budget.
- Keep generated skill documentation and the documentation index synchronized with all five shipped skills.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `skill-and-tool-descriptions`: Defines compact skill discovery metadata and automated description-budget verification.
- `skill-metadata`: Splits primary discovery-time routing boundaries from detailed body guidance.

## Impact

- Five shipped `src/main/skills/*/SKILL.md` files and their patch versions.
- `UpdateSkills` validation, unit tests, generated `docs/skills.md`, and `docs/index.md`.
- No tool metadata, tool implementation, packaging inventory, or best-practices corpus changes.
