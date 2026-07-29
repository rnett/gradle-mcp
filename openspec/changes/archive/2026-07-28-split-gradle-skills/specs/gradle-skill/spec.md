# Capability: gradle-skill

## MODIFIED Requirements

### Requirement: Gradle Skill provides unified build system expertise

The `gradle` skill SHALL provide authoritative guidance for build execution, test running, project introspection, and failure diagnostics using the `gradle` MCP tool and companion tools (`query_build`, `wait_build`, `gradle_docs`). It SHALL NOT cover build script authoring, plugin development, or build performance optimization (use `gradle-build-authoring` for those concerns).

#### Scenario: Agent invokes the gradle tool for a build

- **WHEN** an agent needs to execute any Gradle lifecycle task (build, assemble, compile, jar, etc.)
- **THEN** the `gradle` skill is activated and provides guidance on foreground vs background execution, `captureTaskOutput` usage, and task path syntax

#### Scenario: Agent runs tests with filtering

- **WHEN** an agent needs to execute tests with `--tests` filtering
- **THEN** the `gradle` skill provides test selection pattern guidance (exact class, wildcard, package filter) and per-test failure isolation via `query_build`

#### Scenario: Agent introspects project structure

- **WHEN** an agent needs to map multi-project hierarchy or discover runnable tasks
- **THEN** the `gradle` skill provides guidance on diagnostic tasks (`:projects`, `:tasks`, `:help --task`, `:properties --property`)

#### Scenario: Agent researches official Gradle documentation

- **WHEN** an agent needs to look up Gradle user guide topics or release notes
- **THEN** the `gradle` skill provides `gradle_docs` tag syntax guidance for execution and diagnostic research (`tag:release-notes`, `tag:userguide`). Gradle DSL syntax lookup is a build-authoring concern and routes to `gradle-build-authoring`.

## REMOVED Requirements

### Requirement REMOVED: Gradle Skill includes idiomatic build patterns

The `gradle` skill no longer covers:
- Module creation (moved to `gradle-build-authoring`)
- Performance audits (moved to `gradle-build-authoring`)
- Build logic refactoring (moved to `gradle-build-authoring`)
- Best-practices reference (moved to `gradle-build-authoring`)
- Common build patterns (moved to `gradle-build-authoring`)

#### Scenario: Agent creates a new module

- **WHEN** an agent needs to add a new subproject to a Gradle build
- **THEN** the `gradle` skill's cross-reference section directs the agent to `gradle-build-authoring` for module creation guidance

#### Scenario: Agent needs performance guidance

- **WHEN** an agent asks about build performance or build logic patterns
- **THEN** the `gradle` skill's negative trigger description excludes this concern
- **AND** the `gradle-build-authoring` skill is activated instead

### Requirement REMOVED: Gradle Skill Constitution enforces Gradle MCP tool usage

The `gradle` skill constitution SHALL mandate:
- Using the `gradle` tool instead of raw shell `./gradlew`
- Providing absolute paths for `projectRoot`
- Preferring foreground execution unless tasks are persistent or extremely long-running
- Using `query_build` for all failure diagnostics instead of raw console logs
- Using `captureTaskOutput` for surgical task output isolation
- Using `:properties --property` for surgical property extraction
- Using `gradle_docs` for authoritative documentation lookup

The constitution SHALL NOT include build-authoring rules (lazy APIs, version catalogs, convention plugins, DSL patterns, configuration cache, allprojects/subprojects, Project object access, or configuration-phase resolution).