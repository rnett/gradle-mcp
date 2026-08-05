# Capability: skill-and-tool-descriptions

## Purpose
Establishes structural and terminology standards for MCP skill and tool descriptions, ensuring consistent, authoritative, and discoverable interfaces for AI agents.
## Requirements
### Requirement: Authoritative Skill Descriptions

Each shipped `SKILL.md` MUST provide a non-empty, authoritative, persuasive top-level frontmatter `description` that clearly states why the skill is the preferred way to interact with Gradle for its specific domain. Descriptions MUST start with a third-person gerund (e.g., "Manages...", "Retrieves...", "Analyzes...") as required by the `building-mcp-servers` and `creating-skills` expert guidelines. All skill names MUST be in gerund form (e.g., `managing_gradle_builds`). Descriptions MUST be one or two sentences and state: (1) what the skill does with semantic anchors, (2) when the skill should be activated, and (3) the primary negative routing boundary when the skill would otherwise be ambiguous with another shipped skill. Each parsed description MUST contain no more than 400 characters and MUST NOT repeat detailed workflows, directives, examples, or trigger inventories from the body. Detailed Positive Triggers and Negative Triggers sections MUST appear in the `SKILL.md` body, not in the frontmatter description. If both `description` and `when_to_use` exist, their parsed character counts combined MUST NOT exceed 1,536 characters.

#### Scenario: Agent reads SKILL.md

- **WHEN** an agent reads a `SKILL.md` file
- **THEN** it encounters a 1-2 sentence description that starts with a third-person gerund, uses strong authoritative language (e.g., "authoritatively manage," "STRONGLY PREFERRED"), and includes positive/negative trigger phrases

#### Scenario: Compact discovery metadata

- **WHEN** an agent discovers a shipped skill before loading its body
- **THEN** the parsed frontmatter description contains no more than 400 characters
- **AND** it states the primary negative routing boundary needed to choose among shipped skills

#### Scenario: Optional when-to-use metadata

- **WHEN** a shipped skill defines both `description` and `when_to_use`
- **THEN** their parsed character counts combined do not exceed 1,536 characters

#### Scenario: Detailed trigger guidance

- **WHEN** a shipped skill needs examples or a complete trigger inventory
- **THEN** Positive Triggers and Negative Triggers sections appear in the body
- **AND** frontmatter contains no trigger headings or detailed trigger bullets

### Requirement: Structured Tool Descriptions
Each tool definition in the Kotlin codebase MUST follow a standard markdown structure in its description, including a "Header" (1-2 sentence third-person gerund summary), "Features," "Usage Patterns," "Expert Linkage," and "Cross-References." `@Description` annotations on parameters MUST be **under 100 characters**, specifying type, format, constraints, and a valid example where helpful. Tool descriptions MUST include explicit discriminative language distinguishing them from related tools, including both positive triggers ("Use for...") and negative triggers ("Do NOT use for...") that reference specific alternative tools by name. Parameter descriptions for auto-detected values MUST clearly state when auto-detection applies and when explicit specification is required. A `projectRoot` description MUST instead state the deterministic precedence: expand, resolve, and normalize a nonblank explicit path; otherwise use a nonblank `GRADLE_MCP_PROJECT_ROOT` the same way; otherwise fail with a clear `IllegalArgumentException`. It MUST NOT claim MCP roots discovery.

#### Scenario: Agent lists tools
- **WHEN** an agent lists available tools
- **THEN** each tool's description includes a Header starting with a third-person gerund, a bulleted feature list, clear usage patterns, a skill link, and cross-references to related tools — with no redundant prose that repeats content already in the skill body

#### Scenario: Agent chooses between `gradle` and `gradleOwnSource` tools
- **WHEN** an agent evaluates whether to use `gradleOwnSource: true` on a source tool vs. the `gradle` tool
- **THEN** the `gradleOwnSource` parameter description includes a negative trigger: "Do NOT use for running Gradle builds or tasks — use the `gradle` tool instead"

#### Scenario: Agent decides whether to specify `projectRoot`
- **WHEN** an agent reads the `projectRoot` parameter description
- **THEN** it understands that a nonblank explicit path is expanded, resolved, and normalized first
- **AND** a nonblank `GRADLE_MCP_PROJECT_ROOT` is used only when the explicit value is absent
- **AND** absence of both produces a clear `IllegalArgumentException` rather than MCP roots discovery.

### Requirement: High-Value "When to Use" Scenarios

All skills and tools MUST include "When to Use" guidance that covers specific, high-value engineering scenarios. In SKILL.md frontmatter, this guidance MUST be expressed as terse bullet points or a single discriminative sentence, not prose paragraphs. Detailed scenario prose belongs in the skill body only.

#### Scenario: Agent chooses tool for dependency update

- **WHEN** an agent needs to perform a dependency update audit
- **THEN** it identifies the `inspect_dependencies` tool as the authoritative choice based on a concise scenario keyword (e.g., "Token-Efficient Update Check") in the tool or skill description

### Requirement: Synchronized Terminology
All descriptions across skills and tools MUST use synchronized terminology to ensure a coherent interface for AI agents.

#### Scenario: Cross-referencing between tools and skills
- **WHEN** a tool description mentions "surgical diagnostics"
- **THEN** the corresponding skill uses the same term to describe its advanced failure isolation capabilities

### Requirement: Skill description verification

Skill documentation verification MUST reject shipped metadata whose parsed frontmatter description is missing, blank, longer than 400 characters, or longer than 1,536 characters when combined with optional parsed `when_to_use`. Verification failures MUST identify the skill, measured length, and applicable limit. Malformed or unparseable frontmatter MUST fail verification rather than being skipped.

#### Scenario: Description exceeds budget

- **WHEN** a shipped skill has a parsed description longer than 400 characters
- **THEN** `verifySkillsList` fails with the skill name, measured character count, and 400-character limit

#### Scenario: Combined metadata exceeds limit

- **WHEN** a shipped skill's parsed `description` and `when_to_use` exceed 1,536 characters combined
- **THEN** `verifySkillsList` fails with the skill name, measured combined character count, and 1,536-character limit

