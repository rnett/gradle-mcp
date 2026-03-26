## MODIFIED Requirements

### Requirement: Authoritative Skill Descriptions

Each `SKILL.md` file MUST have an authoritative, persuasive description in its frontmatter that clearly states why it is the preferred way to interact with Gradle for its specific domain. Descriptions MUST start with a third-person gerund (
e.g., "Manages...", "Retrieves...", "Analyzes...") as required by the `building-mcp-servers` and `creating-skills` expert guidelines. All skill names MUST be in gerund form (e.g., `managing_gradle_builds`). Descriptions MUST follow the
three-part structure: (1) what it does with semantic anchors, (2) positive triggers ("Use when..."), (3) negative triggers ("Do NOT use for..."). Descriptions MUST be **1-2 sentences** and MUST NOT repeat guidance already present in the
skill body.

#### Scenario: Agent reads SKILL.md

- **WHEN** an agent reads a `SKILL.md` file
- **THEN** it encounters a 1-2 sentence description that starts with a third-person gerund, uses strong authoritative language (e.g., "authoritatively manage," "STRONGLY PREFERRED"), and includes positive/negative trigger phrases

### Requirement: Structured Tool Descriptions

Each tool definition in the Kotlin codebase MUST follow a standard markdown structure in its description, including a "Header" (1-2 sentence third-person gerund summary), "Features," "Usage Patterns," and "Expert Linkage." `@Description`
annotations on parameters MUST be **under 100 characters**, specifying type, format, constraints, and a valid example where helpful.

#### Scenario: Agent lists tools

- **WHEN** an agent lists available tools
- **THEN** each tool's description includes a Header starting with a third-person gerund, a bulleted feature list, clear usage patterns, and a skill link — with no redundant prose that repeats content already in the skill body

### Requirement: High-Value "When to Use" Scenarios

All skills and tools MUST include "When to Use" guidance that covers specific, high-value engineering scenarios. In SKILL.md frontmatter, this guidance MUST be expressed as terse bullet points or a single discriminative sentence, not prose
paragraphs. Detailed scenario prose belongs in the skill body only.

#### Scenario: Agent chooses tool for dependency update

- **WHEN** an agent needs to perform a dependency update audit
- **THEN** it identifies the `inspect_dependencies` tool as the authoritative choice based on a concise scenario keyword (e.g., "Token-Efficient Update Check") in the tool or skill description

### Requirement: Synchronized Terminology

All descriptions across skills and tools MUST use synchronized terminology to ensure a coherent interface for AI agents.

#### Scenario: Cross-referencing between tools and skills

- **WHEN** a tool description mentions "surgical diagnostics"
- **THEN** the corresponding skill uses the same term to describe its advanced failure isolation capabilities
