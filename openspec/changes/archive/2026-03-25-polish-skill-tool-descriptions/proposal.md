## Why

Skill and tool descriptions have grown verbose over multiple iterations, consuming unnecessary context tokens on every agent invocation. Trimming redundancy and tightening signal-to-noise ratio reduces per-call overhead while preserving all
functional guidance.

## What Changes

- Audit and rewrite all `SKILL.md` frontmatter descriptions to be concise, high-signal summaries (removing filler, redundant phrasing, and over-explained "when to use" prose already covered in the skill body)
- Audit and tighten inline tool descriptions in Kotlin source (`@Description` annotations and `tool<>` description blocks) to remove repetition and verbose justification
- Update `docs/tools/*.md` reference files to reflect tightened descriptions
- Apply `mcp_authoring` and `skill_authoring` authoring skills throughout to validate improvements

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `skill-and-tool-descriptions`: Adding an explicit conciseness requirement — descriptions must minimize token consumption while remaining authoritative and discoverable.
- `skill-metadata`: Adding a constraint that "When to Use" sections use terse bullet scenarios, not prose paragraphs.

## Impact

- All `skills/*/SKILL.md` files
- Tool description strings in `src/main/kotlin/dev/rnett/gradle/mcp/tools/`
- `docs/tools/*.md` generated reference files
- No behavioral or API changes; purely documentation/metadata polish
