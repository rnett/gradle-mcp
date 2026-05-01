# Capability: skill-and-tool-descriptions

## Purpose

Establishes structural and terminology standards for MCP skill and tool descriptions, ensuring consistent, authoritative, and discoverable interfaces for AI agents.

## Requirements

### Requirement: Authoritative Skill Descriptions

Each `SKILL.md` file MUST have an authoritative, persuasive description in its frontmatter that clearly states why it is the preferred way to interact with Gradle for its specific domain. Descriptions MUST start with a third-person gerund (
e.g., "Manages...", "Retrieves...", "Analyzes...") as required by the `building-mcp-servers` and `creating-skills` expert guidelines. All skill names MUST be in gerund form (e.g., `managing_gradle_builds`). Descriptions MUST follow the
three-part structure: (1) what it does with semantic anchors, (2) positive triggers ("Use when..."), (3) negative triggers ("Do NOT use for..."). Descriptions MUST be **1-2 sentences** and MUST NOT repeat guidance already present in the
skill body.

#### Scenario: Agent reads SKILL.md

- **WHEN** an agent reads a `SKILL.md` file
- **THEN** it encounters a 1-2 sentence description that starts with a third-person gerund, uses strong authoritative language (e.g., "authoritatively manage," "STRONGLY PREFERRED"), and includes positive/negative trigger phrases

### Requirement: Structured Tool Descriptions

Each tool definition in the Kotlin codebase MUST follow a standard markdown structure in its description, including a "Header" (1-2 sentence third-person gerund summary), "Features," "Usage Patterns," "Expert Linkage," and "Cross-References." `@Description` annotations on parameters MUST be **under 100 characters**, specifying type, format, constraints, and a valid example where helpful. Tool descriptions MUST include explicit discriminative language distinguishing them from related tools, including both positive triggers ("Use for...") and negative triggers ("Do NOT use for...") that reference specific alternative tools by name. Parameter descriptions for auto-detected values MUST clearly state when auto-detection applies and when explicit specification is required.

#### Scenario: Agent lists tools

- **WHEN** an agent lists available tools
- **THEN** each tool's description includes a Header starting with a third-person gerund, a bulleted feature list, clear usage patterns, a skill link, and cross-references to related tools — with no redundant prose that repeats content already in the skill body

#### Scenario: Agent chooses between `gradle` and `gradleOwnSource` tools

- **WHEN** an agent evaluates whether to use `gradleOwnSource: true` on a source tool vs. the `gradle` tool
- **THEN** the `gradleOwnSource` parameter description includes a negative trigger: "Do NOT use for running Gradle builds or tasks — use the `gradle` tool instead"

#### Scenario: Agent decides whether to specify `projectRoot`

- **WHEN** an agent reads the `projectRoot` parameter description
- **THEN** it clearly understands that auto-detection works when a single MCP root is available
- **AND** it knows to specify `projectRoot` explicitly for multi-root workspaces or when auto-detection fails

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
