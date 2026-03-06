## ADDED Requirements

### Requirement: Persuasive and Technically Grounded Skill Descriptions

Each `SKILL.md` frontmatter SHALL include a persuasive, benefit-oriented description that is significantly more detailed than current descriptions. It SHALL explain the unique value of the skill and incorporate "When to Use" logic directly
into the summary, while remaining grounded in technical reality to avoid "over-descriptiveness" or "fluff."

#### Scenario: Agent chooses skill over shell with detailed context

- **WHEN** the agent reads the detailed skill description for `managing-gradle-builds`
- **THEN** it understands that the `gradle` tool provides superior reliability and visibility through background management and isolated output capturing
- **AND** it uses the `gradle` tool instead of `./gradlew`

### Requirement: Explicit \"When to Use\" Sections

Each `SKILL.md` SHALL have a "When to Use" section with at least 3 specific, high-signal scenarios that guide the agent in tool selection. These scenarios SHALL also be reflected in the primary description.

#### Scenario: High-signal selection

- **WHEN** the agent is unsure which tool to use for a large test suite
- **THEN** it finds a scenario in the `executing-gradle-tests` skill: "When running large test suites that require background monitoring and detailed failure isolation"
- **AND** it selects the `executing-gradle-tests` skill

### Requirement: Authoritative Task Path Syntax Documentation

The `managing-gradle-builds`, `executing-gradle-tests`, and `introspecting-gradle-projects` skills SHALL include authoritative information about Gradle project and task path syntax, specifically clarifying the difference between "task
selectors" and "absolute task
paths."

#### Scenario: Agent correctly targets tasks in all projects

- **WHEN** the agent needs to run tests in all subprojects
- **THEN** it reads the authoritative path documentation in the `executing-gradle-tests` skill
- **AND** it uses the task selector `test` (without a leading colon) to run tests across all projects

#### Scenario: Agent correctly targets a root project task

- **WHEN** the agent needs to run a task only in the root project
- **THEN** it reads the authoritative path documentation in the `managing-gradle-builds` skill
- **AND** it uses an absolute path like `:test` (with a leading colon) to target the root project specifically

### Requirement: Expanded and Structured Tool Descriptions

Each MCP tool description SHALL highlight its unique value proposition and provide comprehensive guidance on its advanced features. These descriptions SHALL be significantly longer to provide complete context, but MUST preserve all
functional and technical details. For long tool descriptions, functional guidance SHALL be clearly labeled and positioned after the high-level summary.

#### Scenario: Detailed tool guidance

- **WHEN** the agent reads the expanded `inspect_build` description
- **THEN** it learns about specific usage patterns like "Wait for Log" or "Surgical Diagnostics"
- **AND** it can access technical details on how to use each feature without further guesswork or exploration

### Requirement: Argument-Level Guidance with Efficiency Hints

Data classes for tool arguments SHALL use `@Description` annotations that provide hints about efficient usage and best practices.

#### Scenario: Pagination hint with reasoning

- **WHEN** the agent is using a tool with a `limit` parameter
- **THEN** it sees a description: "Maximum number of results to return. Use a smaller limit for large projects to reduce context usage and improve response time."
- **AND** it sets a reasonable limit
