## Context

Skill and tool descriptions have accumulated verbose prose over multiple iterations. Each agent invocation loads all SKILL.md frontmatter descriptions and tool description strings into context — meaning every unnecessary word is paid for
repeatedly. The authoring standards (`mcp_authoring`, `skill_authoring`) provide the canonical quality criteria to apply.

Current pain points:

- SKILL.md frontmatter descriptions repeat guidance already in the skill body
- Tool `@Description` annotations include multi-sentence justifications that should be one-liners
- "When to Use" prose in frontmatter duplicates structured sections inside the skill body
- `docs/tools/*.md` reference docs may be stale relative to the source

## Goals / Non-Goals

**Goals:**

- Reduce average tokens per skill/tool description without losing authoritative signal
- Apply `mcp_authoring` and `skill_authoring` review criteria to all descriptions
- Add explicit conciseness requirements to the `skill-and-tool-descriptions` and `skill-metadata` specs

**Non-Goals:**

- Restructuring skill body content or adding new guidance sections
- Changing tool behavior, parameters, or functional semantics
- Renaming tools or skills

## Decisions

### Decision: Audit-first, edit-second

Run `skill_authoring` and `mcp_authoring` skills against each file before editing. This grounds rewrites in the established criteria rather than subjective trimming.

*Alternative considered*: Rewrite from scratch. Rejected — risks losing hard-won domain-specific guidance that took multiple iterations to develop.

### Decision: Frontmatter description = 1-2 sentences, three-part structure

Per `skill_authoring`: capability + semantic anchors → positive triggers → negative triggers. The body is loaded on activation; the description only needs to discriminate between skills. Verbose "when to use" prose belongs in the skill body
only.

*Alternative considered*: Keep long descriptions for richer selection context. Rejected — the body is loaded when the skill is activated; the description only needs to discriminate between options.

### Decision: Tool body descriptions = 1-2 sentence opening + "how to" only when required

Per `mcp_authoring`: open with a high-signal summary, then include additional guidance only when the tool requires non-obvious usage patterns. Eliminate prose rationale that duplicates the skill body.

### Decision: `@Description` annotations = under 100 characters

Per `mcp_authoring`: parameter descriptions specify type, format, constraints, and a valid example. Hard cap at 100 characters prevents schema bloat.

### Decision: Update docs/tools/*.md after Kotlin source is finalized

The docs files are generated/derived. Edit them last, after all source descriptions are settled, to avoid double-work.

## Risks / Trade-offs

- **Risk**: Over-trimming loses guidance an agent needs mid-task → Mitigation: Preserve all MUST/NEVER/ALWAYS directives; only cut prose rationale and examples that are repeated elsewhere.
- **Risk**: Frontmatter changes break skill selection routing → Mitigation: Validate with `mcp_authoring` skill after each rewrite; keep discriminative keywords.

## Open Questions

None — scope is well-defined.
