## ADDED Requirements

### Requirement: Discriminative Tool Descriptions

Each tool description MUST include explicit discriminative language that distinguishes it from related tools. Descriptions MUST include both positive triggers ("Use for...") and negative triggers ("Do NOT use for...") that reference
specific alternative tools by name.

#### Scenario: Agent chooses between `gradle` and `gradleOwnSource` tools

- **WHEN** an agent evaluates whether to use `gradleOwnSource: true` on a source tool vs. the `gradle` tool
- **THEN** the `gradleOwnSource` parameter description includes a negative trigger: "Do NOT use for running Gradle builds or tasks — use the `gradle` tool instead"

### Requirement: Cross-Reference Between Related Tools

Tool descriptions MUST include cross-references to related tools when they serve adjacent but distinct purposes. Cross-references MUST use the exact tool name as defined in `ToolNames`.

#### Scenario: Agent navigates from `gradle` to `query_build`

- **WHEN** an agent reads the `gradle` tool description
- **THEN** it encounters a cross-reference: "For post-build diagnostics, test results, and task output, use `query_build`"

#### Scenario: Agent navigates from source tools to `gradle`

- **WHEN** an agent reads the `searchDependencySources` or `readDependencySources` tool descriptions
- **THEN** it encounters a cross-reference: "To run Gradle tasks or builds, use the `gradle` tool"

### Requirement: Clear Auto-Detection Boundaries

Parameter descriptions for auto-detected values MUST clearly state when auto-detection applies and when explicit specification is required. Ambiguous terms like "usually" or "typically" MUST be replaced with specific conditions.

#### Scenario: Agent decides whether to specify `projectRoot`

- **WHEN** an agent reads the `projectRoot` parameter description
- **THEN** it clearly understands that auto-detection works when a single MCP root is available
- **AND** it knows to specify `projectRoot` explicitly for multi-root workspaces or when auto-detection fails
