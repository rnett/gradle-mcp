## MODIFIED Requirements

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
