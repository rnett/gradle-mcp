# gradle-skill

## Purpose

Defines the `gradle` skill, the entry point for all Gradle build execution, test running, project introspection, and failure diagnostics using the `gradle` MCP tool.

## MODIFIED Requirements

### Requirement: Gradle Skill provide unified build system expertise

The `gradle` skill SHALL provide authoritative guidance for build execution, test running, project introspection, and failure diagnostics using the `gradle` MCP tool and companion tools (`query_build`, `wait_build`, `gradle_docs`). It SHALL NOT cover build script authoring, plugin development, or build performance optimization (use `gradle-build-authoring` for those concerns).

#### Scenario: Agent invokes the gradle tool for a build

- **WHEN** an agent needs to execute any Gradle lifecycle task (build, assemble, compile, jar, etc.)
- **THEN** the `gradle` skill is activated and provides guidance on foreground vs background execution, `captureTaskOutput` usage, and task path syntax

#### Scenario: Agent runs tests with filtering

- **WHEN** an agent needs to execute tests with `--tests` filtering
- **THEN** the `gradle` skill provides test selection pattern guidance (exact class, wildcard, package filter) and per-test failure isolation via `query_build`. If the build uses `jvm-test-suite`, the agent is guided to discover and run the specific suite task instead of assuming it is part of `test` or `check`.

#### Scenario: Agent introspects project structure

- **WHEN** an agent needs to map multi-project hierarchy or discover runnable tasks
- **THEN** the `gradle` skill provides guidance on diagnostic tasks (`:projects`, `:tasks`, `:help --task`, `:properties --property`)

#### Scenario: Agent researches official Gradle documentation

- **WHEN** an agent needs to look up Gradle user guide topics or release notes
- **THEN** the `gradle` skill provides `gradle_docs` tag syntax guidance for execution and diagnostic research (`tag:release-notes`, `tag:userguide`). Gradle DSL syntax lookup is a build-authoring concern and routes to `gradle-build-authoring`.

### Requirement: Gradle Skill Constitution enforces Gradle MCP tool usage

The `gradle` skill constitution SHALL mandate:

- Using the `gradle` tool instead of raw shell `./gradlew`
- Providing absolute paths for `projectRoot`
- Preferring foreground execution unless tasks are persistent or extremely long-running
- Using `query_build` for all failure diagnostics instead of raw console logs
- Using `captureTaskOutput` for surgical task output isolation
- Using `:properties --property` for surgical property extraction
- Using `gradle_docs` for authoritative documentation lookup

#### Scenario: Agent validates third-party plugin artifacts

- **WHEN** an agent needs to verify artifacts produced by third-party plugins (e.g. Shadow, Vanniktech, BuildConfig)
- **THEN** the skill directs the agent to use `outgoingVariants` and resolvable-configuration reports, and verify `publishToMavenLocal` output instead of assuming plugin DSL or publication names.

#### Scenario: Agent introspects a build's entry point

- **WHEN** an agent first encounters a checkout
- **THEN** the skill guides the agent to treat checked-in CI workflow files (e.g. `.github/workflows/*.yml`) as operational evidence of canonical task paths, suites, and JDKs, confirming these against the evaluated build model.

#### Scenario: Agent identifies valid build boundaries

- **WHEN** an agent evaluates the root structure
- **THEN** the skill recognizes that a settings-only root (no root `build.gradle(.kts)`) is a valid build, and that a coexisting root `pom.xml` marks Maven and Gradle as separate candidate boundaries, requiring README/CI evidence to select the entry point.

#### Scenario: Agent debugs a build failure

- **WHEN** a build fails
- **THEN** the skill directs the agent to use `query_build(kind="FAILURES")` and `query_build(kind="PROBLEMS")` before reading raw console logs

#### Scenario: Agent introspects a task

- **WHEN** an agent needs task output or property values
- **THEN** the skill directs the agent to use `captureTaskOutput` with the appropriate task path rather than parsing full console output

## REMOVED Requirements

### Requirement REMOVED: Shared query_build diagnostics reference eliminates duplication

This requirement (shared `query_build_diagnostics.md` reference file) is removed because the `gradle` skill is trimmed to execution/diagnostics only; detailed reference material belongs in the `gradle` skill itself as concise inline guidance.

### Requirement REMOVED: Gradle Skill includes idiomatic build patterns

The `gradle` skill no longer covers:

- Module creation (moved to `gradle-build-authoring`)
- Performance audits (moved to `gradle-build-authoring`)
- Build logic refactoring (moved to `gradle-build-authoring`)
- Best-practices reference (moved to `gradle-build-authoring`)
- Common build patterns (moved to `gradle-build-authoring`)

## Cross-references

When a question involves build-script modification, module creation, plugin development, or build performance, the `gradle` skill MUST direct the agent to `gradle-build-authoring`.
