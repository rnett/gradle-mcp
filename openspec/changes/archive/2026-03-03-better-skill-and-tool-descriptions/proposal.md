## Why

The current descriptions for Gradle MCP tools and skills are often functional but not sufficiently persuasive or detailed. Agents (LLMs) may default to less efficient raw shell commands or guess parameters instead of leveraging the
high-signal, managed features provided by the Gradle MCP server. Improving these descriptions by making them more comprehensive and benefit-oriented will lead to better tool selection, reduced context usage through more precise calls, and
more robust build/test execution.

## What Changes

- **Skill Frontmatter**: Rewrite descriptions to be more benefit-oriented, authoritative, and significantly more detailed.
- **Tool Descriptions**: Refine and expand to highlight unique advantages like background management, task output capturing, and token efficiency.
- **When to Use**: Expand with specific, high-value scenarios for each skill, ensuring these scenarios are also integrated into the overall description.
- **Consistency**: Standardize terminology across all skills and tools to ensure clear cross-referencing.
- **Depth**: Embrace longer, more thorough descriptions to provide agents with complete context and reduce ambiguity.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `gradle-build`: Improve description and \"When to Use\" section.
- `gradle-test`: Improve description and \"When to Use\" section.
- `gradle-dependencies`: Improve description and \"When to Use\" section.
- `gradle-docs`: Improve description and \"When to Use\" section.
- `gradle-introspection`: Improve description and \"When to Use\" section.
- `gradle-library-sources`: Improve description and \"When to Use\" section.
- `gradle-repl`: Improve description and \"When to Use\" section.
- `compose-view`: Improve description and \"When to Use\" section.

## Impact

- All `SKILL.md` files in the `skills/` directory.
- All tool definitions in `src/main/kotlin/dev/rnett/gradle/mcp/tools/`.
- Updated documentation in `docs/tools/` and `docs/skills.md`.

