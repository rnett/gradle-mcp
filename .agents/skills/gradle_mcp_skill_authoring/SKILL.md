---
name: gradle_mcp_skill_authoring
description: >-
  Project-specific workflows for creating and maintaining high-quality agentic skills within the Gradle MCP repository.
metadata:
  author: rnett
  version: "1.0"
---

# Skill: Gradle MCP Skill Authoring

This skill provides project-specific guidance for creating and maintaining agentic skills within the Gradle MCP project. These skills guide other agents in solving complex Gradle tasks.

## Constitution

- **Local Scope**: "Update skills" directives ALWAYS refer to skills in `./skills/`, NEVER global skills, unless explicitly specified.
- **Synchronization**: When modifying a tool's behavior or metadata, you MUST update all referencing skills in `./skills/` to ensure they remain consistent.
- **Metadata Consistency**: Skills in a given commit must work with the MCP tools in that same commit.

## Workflow

### 1. Creation

- Create a new directory in `skills/` with a descriptive `snake_case` name.
- Create a `SKILL.md` file with the required YAML frontmatter and instructions.
- Follow the "Expert Tone" and "Progressive Disclosure" principles.

### 2. Refinement

- **Lean Body**: Keep the `SKILL.md` body focused on high-level rules and workflows.
- **Deep-Dives**: Move exhaustive technical details or large reference blocks to the `references/` subdirectory.
- **Deterministic Logic**: Move exact sequences or scripts to `scripts/`.

### 3. Registration

- Update `docs/skills.md` with the name and description of the new skill.
- Ensure the skill is discoverable by other agents.

### 4. Referencing

- Use relative links to living files within the repository to avoid "instruction rot".
- **Restriction**: Do NOT link directly to library source code from skills, as these links may break or be inaccessible in different environments.
- Verify cross-skill links carefully before adding them.

## Examples

### Creating a new Gradle-related skill

1. Create `skills/my_new_skill/SKILL.md`.
2. Define the capability: "Guides agents in optimizing Gradle build performance...".
3. Add to `docs/skills.md`.
4. If it uses `gradle` tool, ensure the instructions match the current tool parameters.
