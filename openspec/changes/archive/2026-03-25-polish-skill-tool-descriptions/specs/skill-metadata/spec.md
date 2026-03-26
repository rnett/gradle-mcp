## MODIFIED Requirements

### Requirement: Persuasive and Technically Grounded Skill Descriptions

Each `SKILL.md` frontmatter SHALL include a persuasive, benefit-oriented description that explains the unique value of the skill and incorporates high-signal "When to Use" discriminators. Descriptions SHALL be grounded in technical reality
to avoid fluff, and SHALL be **1-2 sentences** following the three-part structure: capability + semantic anchors, positive triggers, negative triggers. Detailed usage examples and multi-step workflows SHALL appear in the skill body, not the
frontmatter.

#### Scenario: Agent chooses skill over shell with detailed context

- **WHEN** the agent reads the skill description for `managing-gradle-builds`
- **THEN** it understands in one concise paragraph that the `gradle` tool provides managed background builds and task output capturing
- **AND** it uses the `gradle` tool instead of `./gradlew`

### Requirement: Explicit "When to Use" Sections

Each `SKILL.md` SHALL have a "When to Use" section with at least 3 specific, high-signal scenarios. In the frontmatter description, "When to Use" context SHALL be expressed as brief discriminative keywords or a single sentence — not a prose
list. Full scenario detail belongs in the body section only.

#### Scenario: High-signal selection

- **WHEN** the agent is unsure which tool to use for a large test suite
- **THEN** it finds a terse scenario keyword in the `executing-gradle-tests` skill description (e.g., "large test suite monitoring")
- **AND** it activates the skill to get full scenario detail

### Requirement: Authoritative Task Path Syntax Documentation

The `managing-gradle-builds`, `executing-gradle-tests`, and `introspecting-gradle-projects` skills SHALL include authoritative information about Gradle project and task path syntax, specifically clarifying the difference between task
selectors and absolute task paths.

#### Scenario: Agent correctly targets tasks in all projects

- **WHEN** the agent needs to run tests in all subprojects
- **THEN** it reads the authoritative path documentation in the `executing-gradle-tests` skill
- **AND** it uses the task selector `test` (without a leading colon) to run tests across all projects

#### Scenario: Agent correctly targets a root project task

- **WHEN** the agent needs to run a task only in the root project
- **THEN** it reads the authoritative path documentation in the `managing-gradle-builds` skill
- **AND** it uses an absolute path like `:test` (with a leading colon) to target the root project specifically

### Requirement: Expanded and Structured Tool Descriptions

Each MCP tool description SHALL open with a 1-2 sentence high-signal summary (third-person gerund, semantic anchors), followed by additional "how to" guidance only when required for correct usage. Descriptions SHALL preserve all functional
MUST/NEVER/ALWAYS directives and usage patterns, but SHALL eliminate prose rationale that duplicates the skill body.

#### Scenario: Detailed tool guidance without redundancy

- **WHEN** the agent reads the `inspect_build` description
- **THEN** it learns specific usage patterns (e.g., "Wait for Log," "Surgical Diagnostics") without encountering repeated justifications already present in the `running_gradle_builds` skill

### Requirement: Argument-Level Guidance with Efficiency Hints

Data classes for tool arguments SHALL use `@Description` annotations that provide concise hints about type, format, constraints, and a valid example. Annotations SHALL be **under 100 characters**.

#### Scenario: Pagination hint with reasoning

- **WHEN** the agent is using a tool with a `limit` parameter
- **THEN** it sees a sub-100-character description: "Max results to return; use smaller values on large projects to reduce context."
- **AND** it sets a reasonable limit
