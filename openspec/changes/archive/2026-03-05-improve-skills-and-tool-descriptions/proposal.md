## Why

The current descriptions for Gradle MCP tools and skills are highly functional, but there is still room for further optimization to ensure they are as high-signal, authoritative, and persuasive as possible for AI agents. By providing even
more explicit guidance on "When to Use" each tool and skill, and by further refining the terminology to be perfectly consistent across the entire project, we can ensure that agents always choose the most efficient and robust path for
Gradle-related tasks. This reduces token waste, prevents brittle shell-based workarounds, and ensures that the advanced features of the Gradle MCP server (like background management and surgical output capturing) are fully leveraged.

## What Changes

- **Authoritative Refinement**: Another pass over all tool descriptions and `SKILL.md` files to ensure they use strong, authoritative language that prioritizes MCP tools over shell commands.
- **Gerund-First Descriptions**: Rewrite all tool and skill descriptions to start with a third-person gerund (e.g., "Analyzes...", "Retrieves...", "Manages...") as required by the `building-mcp-servers` and `creating-skills` expert
  guidelines.
- **Skill Name Normalization**: Ensure all skill names use the action-oriented gerund form (e.g., `managing_gradle_builds` is already good, but check others).
- **Scenario Expansion**: Add more specific, high-value scenarios to "When to Use" sections in both tools and skills, particularly for complex dependency and source exploration tasks.
- **Terminology Synchronization**: Rigorously audit all descriptions to ensure terms like "surgical diagnostics," "progressive disclosure," and "managed background execution" are used consistently and cross-referenced correctly.
- **Enhanced Tool-to-Skill Linkage**: Ensure every tool description explicitly points to the relevant skill for "expert-level workflows," creating a clear hierarchy of information.
- **Input Parameter Polishing**: Refine `@Description` annotations for tool parameters to be more descriptive and provide clearer defaults and usage hints.

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `managing_gradle_builds`: Further refine description and scenarios for background task management.
- `executing_gradle_tests`: Enhance guidance on surgical test selection and failure isolation.
- `managing_gradle_dependencies`: Improve authoritative dependency auditing and update check descriptions.
- `introspecting_gradle_projects`: Polished descriptions for project structure and environment mapping.
- `searching_gradle_sources`: Enhanced guidance on high-performance symbol and full-text searching.
- `prototyping_gradle_logic`: Improved description for interactive Kotlin prototyping.
- `reading_gradle_docs`: Refined search and retrieval guidance for official documentation.
- `verifying_compose_ui`: Polished description for visual Compose UI verification.
- `install-gradle-skills`: Improved authoritative skill management description.

## Impact

- All `SKILL.md` files in the `skills/` directory.
- Tool definitions in `src/main/kotlin/dev/rnett/gradle/mcp/tools/` and subdirectories.
- Updated documentation in `docs/tools/` and `docs/skills.md`.
