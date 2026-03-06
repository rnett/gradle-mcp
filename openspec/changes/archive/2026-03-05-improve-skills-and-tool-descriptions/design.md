## Context

The Gradle MCP server provides advanced capabilities for managing Gradle builds, but their effectiveness depends heavily on how they are presented to AI agents. The current descriptions are functional, but we aim for a higher level of
persuasiveness and precision to ensure agents consistently choose MCP tools over less reliable shell commands.

## Goals / Non-Goals

**Goals:**

- **Elevated Professionalism**: Use strong, authoritative, and benefits-oriented language.
- **Perfect Consistency**: Standardize terminology across all tools and skills.
- **Surgical Precision**: Ensure "When to Use" scenarios are explicit and high-value.
- **Deep Context**: Provide comprehensive tool and parameter descriptions to reduce agent ambiguity.

**Non-Goals:**

- **Functional Changes**: This change is strictly about documentation and descriptions; no code behavior will be modified.
- **New Tools**: No new tools or skills will be added.

## Decisions

- **Template-Based Authoring**: Adopt a standard structure for all tool descriptions:
    - **Header**: One-sentence authoritative summary.
    - **Features**: Bulleted list of unique capabilities (e.g., "High-Performance Features," "Authoritative Features").
    - **Usage Patterns**: Clear, copy-pasteable examples of common calls.
    - **Expert Linkage**: Direct reference to the corresponding `SKILL.md` for deep-dive workflows.
- **Authoritative Terminology**: Standardize on key terms:
    - `Authoritative Tool`: Use to describe the MCP tool as the primary/preferred source.
    - `Surgical Precision`: Use for filtering, output capturing, and targeted diagnostics.
    - `Managed Lifecycle`: Use for background build monitoring and caching.
    - `Token-Efficient`: Use to highlight reduced context usage.
- **Gerund-First Descriptions (Mandatory)**: All tool and skill descriptions MUST start with a third-person gerund (e.g., "Manages...", "Retrieves...", "Analyzes..."). This is a core mandate from `building-mcp-servers` and `creating-skills`
  for providing clear affordances to AI agents.
- **Third-Person Voice**: Descriptions must be in the third person ("Analyzes...") rather than imperative ("Analyze...") to indicate the tool's capabilities objectively.
- **Skill Name Gerund Form**: All skills must be named using an action-oriented gerund form (e.g., `managing_gradle_builds`, `optimizing-code`). Existing skill names will be audited for compliance.
- **Progressive Documentation**: Ensure `docs/tools/` is automatically updated via `updateToolsList` to maintain a human-readable reference that mirrors the agent-facing descriptions.

## Risks / Trade-offs

- [Risk] → Descriptions become too long and consume excessive context.
- [Mitigation] → Use concise, high-signal language and structured formatting (markdown tables, bullet points) to maximize information density.
- [Risk] → Inconsistent terminology between new and old descriptions.
- [Mitigation] → Perform a final "synchronization audit" across all updated files before completion.
