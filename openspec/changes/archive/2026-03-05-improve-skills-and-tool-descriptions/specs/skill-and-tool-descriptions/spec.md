## ADDED Requirements

### Requirement: Authoritative Skill Descriptions

Each `SKILL.md` file MUST have an authoritative, persuasive description in its frontmatter that clearly states why it is the preferred way to interact with Gradle for its specific domain. Descriptions MUST start with a third-person gerund (
e.g., "Manages...", "Retrieves...", "Analyzes...") as required by the `building-mcp-servers` and `creating-skills` expert guidelines. All skill names MUST be in gerund form (e.g., `managing_gradle_builds`).

#### Scenario: Agent reads SKILL.md

- **WHEN** an agent reads a `SKILL.md` file
- **THEN** it encounters a description that starts with a third-person gerund, uses strong, authoritative language (e.g., "authoritatively manage," "STRONGLY PREFERRED"), and explicitly lists the tools it manages.

### Requirement: Structured Tool Descriptions

Each tool definition in the Kotlin codebase MUST follow a standard markdown structure in its description, including "Header," "Features," "Usage Patterns," and "Expert Linkage." The Header MUST start with a third-person gerund.

#### Scenario: Agent lists tools

- **WHEN** an agent lists available tools
- **THEN** each tool's description includes a Header starting with a third-person gerund, a bulleted list of high-performance features, clear usage examples, and a link to the corresponding skill for expert-level workflows.

### Requirement: High-Value "When to Use" Scenarios

All skills and tools MUST include expanded "When to Use" sections that cover specific, high-value engineering scenarios, ensuring agents can easily match their current task to the appropriate tool.

#### Scenario: Agent chooses tool for dependency update

- **WHEN** an agent needs to perform a dependency update audit
- **THEN** it identifies the `inspect_dependencies` tool as the authoritative choice based on a scenario like "Token-Efficient Update Check" in the tool or skill description.

### Requirement: Synchronized Terminology

All descriptions across skills and tools MUST use synchronized terminology to ensure a coherent and professional interface for AI agents.

#### Scenario: Cross-referencing between tools and skills

- **WHEN** a tool description mentions "surgical diagnostics"
- **THEN** the corresponding skill uses the same term to describe its advanced failure isolation capabilities, reinforcing the agent's understanding.
