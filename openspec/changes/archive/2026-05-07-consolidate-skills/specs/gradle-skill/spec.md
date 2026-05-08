## ADDED Requirements

### Requirement: Gradle Skill provides unified build system expertise

The `gradle` skill SHALL provide authoritative guidance for ALL operations using the `gradle` MCP tool and its companion tools (`query_build`, `wait_build`, `gradle_docs`). It MUST consolidate the workflows previously spread across
`running_gradle_builds`, `running_gradle_tests`, `introspecting_gradle_projects`, and `gradle_expert`.

#### Scenario: Agent invokes the gradle tool for a build

- **WHEN** an agent needs to execute any Gradle lifecycle task (build, assemble, compile, jar, etc.)
- **THEN** the `gradle` skill is activated and provides guidance on foreground vs background execution, `captureTaskOutput` usage, and task path syntax

#### Scenario: Agent runs tests with filtering

- **WHEN** an agent needs to execute tests with `--tests` filtering
- **THEN** the `gradle` skill provides test selection pattern guidance (exact class, wildcard, package filter) and per-test failure isolation via `query_build`

#### Scenario: Agent introspects project structure

- **WHEN** an agent needs to map multi-project hierarchy or discover runnable tasks
- **THEN** the `gradle` skill provides guidance on diagnostic tasks (`:projects`, `:tasks`, `:help --task`, `:properties --property`)

#### Scenario: Agent creates a new module

- **WHEN** an agent needs to add a new subproject to a Gradle build
- **THEN** the `gradle` skill provides the module creation workflow (directory structure, `settings.gradle.kts` inclusion, `build.gradle.kts` creation, verification)

#### Scenario: Agent researches official Gradle documentation

- **WHEN** an agent needs to look up Gradle DSL syntax, user guide topics, or release notes
- **THEN** the `gradle` skill provides `gradle_docs` tag syntax guidance (`tag:dsl`, `tag:userguide`, `tag:release-notes`, `tag:samples`, `tag:javadoc`, `tag:best-practices`)

### Requirement: Gradle Skill Constitution enforces Gradle MCP tool usage

The `gradle` skill constitution SHALL mandate:

- Using the `gradle` tool instead of raw shell `./gradlew`
- Providing absolute paths for `projectRoot`
- Preferring foreground execution unless tasks are persistent or extremely long-running
- Using `query_build` for all failure diagnostics instead of raw console logs
- Using `captureTaskOutput` for surgical task output isolation
- Preferring `register` over `create` (lazy APIs) for task declarations
- Using version catalogs (`libs.versions.toml`) for dependency management when present
- Verifying behavior against `gradle_docs` before assuming API semantics

#### Scenario: Agent debugs a build failure

- **WHEN** a build fails
- **THEN** the skill directs the agent to use `query_build(kind="FAILURES")` and `query_build(kind="PROBLEMS")` before reading raw console logs

#### Scenario: Agent introspects a task

- **WHEN** an agent needs task output or property values
- **THEN** the skill directs the agent to use `captureTaskOutput` with the appropriate task path rather than parsing full console output

### Requirement: Shared query_build diagnostics reference eliminates duplication

A single reference file at `gradle/references/query_build_diagnostics.md` SHALL contain all `query_build` diagnostic patterns. It MUST cover:

- Build dashboard and summary inspection
- Failure inspection (`kind="FAILURES"`)
- Problem inspection (`kind="PROBLEMS"`)
- Task output inspection (`kind="TASKS"`)
- Test inspection (`kind="TESTS"`, both summary and per-test details)
- Console log inspection (`kind="CONSOLE"`)
- Progress monitoring with `wait_build`
- Pagination and output file export

#### Scenario: Agent needs test failure details

- **WHEN** an agent accesses the `query_build` diagnostics reference
- **THEN** the reference provides JSON examples for listing failed tests and retrieving per-test stack traces

#### Scenario: Agent needs build-level failure analysis

- **WHEN** an agent accesses the `query_build` diagnostics reference
- **THEN** the reference provides JSON examples for inspecting failures, problems, and task outputs

### Requirement: Gradle Skill includes idiomatic build patterns

The `gradle` skill SHALL include reference files covering:

- Best practices for Gradle build logic (`gradle/references/best_practices.md`), migrated from `gradle_expert`
- Common build patterns (`gradle/references/common_build_patterns.md`), including multi-project structure, convention plugins, custom task registration, and standard plugin configuration

#### Scenario: Agent needs to set up a convention plugin

- **WHEN** an agent asks about sharing build logic across subprojects
- **THEN** the skill's common build patterns reference provides the `build-logic` convention plugin pattern with code examples

#### Scenario: Agent needs performance guidance

- **WHEN** an agent asks about build performance
- **THEN** the skill directs to `gradle_docs` with `tag:best-practices` for the latest official guidance
