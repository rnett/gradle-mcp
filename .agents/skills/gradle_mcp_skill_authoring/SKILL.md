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

- **Skill Locations**: This repo has TWO skill directories — distinguish them carefully:
    - `src/main/skills/` — distributable skills shipped to consumers via `install_gradle_skills` (registered in `docs/skills.md`).
    - `.agents/skills/` — local-only agent skills used while working in this repo (the GMCP-internal expert skills like this one).
- **Local Scope**: "Update skills" directives ALWAYS refer to skills in this repo (`.agents/skills/` or `src/main/skills/`, depending on context), NEVER global agent-config skills, unless explicitly specified.
- **Synchronization**: When modifying a tool's behavior or metadata, you MUST update all referencing skills in both `src/main/skills/` and `.agents/skills/` to keep them consistent.
- **Metadata Consistency**: Skills in a given commit must work with the MCP tools in that same commit.

## Workflow

### 1. Creation

- Create a new directory under the appropriate skills root (`src/main/skills/` for distributable, `.agents/skills/` for local-only) with a descriptive `snake_case` name.
- Create a `SKILL.md` file with the required YAML frontmatter and instructions.
- Follow the "Expert Tone" and "Progressive Disclosure" principles.

### 2. Refinement

- **Lean Body**: Keep the `SKILL.md` body focused on high-level rules and workflows.
- **Deep-Dives**: Move exhaustive technical details or large reference blocks to the `references/` subdirectory.
- **Deterministic Logic**: Move exact sequences or scripts to `scripts/`.

### 3. Registration

- For distributable skills under `src/main/skills/`: update `docs/skills.md` with the name and description so consumers can discover them.
- For local skills under `.agents/skills/`: ensure they are referenced from `AGENTS.md` (the "Local Agent Skills" section) so contributors know they exist and must keep them current.
- Ensure the skill is discoverable by other agents.

### 4. Referencing

- Use relative links to living files within the repository to avoid "instruction rot".
- **Restriction**: Do NOT link directly to library source code from skills, as these links may break or be inaccessible in different environments.
- Verify cross-skill links carefully before adding them.

## Examples

### Creating a new Gradle-related distributable skill

1. Create `src/main/skills/my_new_skill/SKILL.md`.
2. Define the capability: "Guides agents in optimizing Gradle build performance...".
3. Add to `docs/skills.md`.
4. If it uses `gradle` tool, ensure the instructions match the current tool parameters.

### Creating a new local agent skill

1. Create `.agents/skills/my_internal_skill/SKILL.md`.
2. Reference it from the "Local Agent Skills" section of `AGENTS.md`.
