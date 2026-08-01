# Capability: using-gradle

## Description
Schedules and performs root-level Gradle operations for inspecting and operating existing builds.

### Use when
- Orienting in an unfamiliar existing build by reading the wrapper version, mapping modules, and discovering entry-point tasks.
- Executing Gradle tasks in foreground or background.
- Monitoring build progress or capturing isolated task output.
- Diagnosing build failures through filtered test execution or diagnostic tasks.
- Researching official Gradle documentation, release notes, or internal APIs.
- Auditing the dependency graph, resolving version conflicts, or discovering library updates.
- Searching and reading source code for dependencies, plugins, Gradle itself, or the JDK.
- Making trivial everyday dependency edits, such as adding a version-catalog entry and library declaration or bumping a version.

### Do NOT use
- Structural build authoring: adding or changing plugins, repositories, modules or subprojects, toolchains, publishing, or CI wiring (use `authoring-gradle-builds`).
- Configuring compiler options or testing frameworks.
- Executing arbitrary Kotlin/Java code via the REPL.
- Rendering Compose UI components.

## Purpose

This capability defines the workflows and requirements for operating Gradle builds.
## Requirements
### Requirement: Broad Operational Index
The skill MUST provide a compact workflow index that directs an AI feature developer operating an existing Gradle build to specialized procedures for project mapping, execution, testing, diagnosis, dependency inspection, and source research.

#### Scenario: Select an execution procedure
- **WHEN** an agent needs to run an unfamiliar task
- **THEN** it reads the wrapper version, maps projects, discovers the task with authoritative task help, and loads the running-builds reference before invoking the Gradle MCP execution tools

#### Scenario: Interpret a successful task
- **WHEN** a task returns a successful result
- **THEN** the agent reads whether it was `EXECUTED`, `UP-TO-DATE`, `FROM-CACHE`, `NO-SOURCE`, `SKIPPED`, or `EXCLUDED` before treating the result as proof that work occurred
- **AND** it uses targeted `--rerun` rather than broad `--rerun-tasks` when the wrapper supports `--rerun`, with a documented fallback for older Gradle versions

#### Scenario: Handle execution footguns
- **WHEN** an agent uses task-execution flags while diagnosing a build
- **THEN** the running-builds guidance explains that `--continue` does not run tasks whose prerequisites failed, `--offline` can reuse stale cached dependencies, and `--warning-mode` changes deprecation reporting rather than fixing the warning

### Requirement: Dependency and Source Research
The skill MUST consolidate dependency-graph auditing, version and conflict investigation, update discovery, and dependency, plugin, Gradle, and JDK source reading into the `using-gradle` workflow.

#### Scenario: Trace a dependency conflict
- **WHEN** an agent identifies a version conflict in the dependency tree
- **THEN** it uses `inspect_dependencies` and `dependencyInsight` to inspect requested versions, constraints, repository order, provenance, and the selected winner before changing the declaration

#### Scenario: Account for dependency freshness
- **WHEN** a dynamic version, changing module, or `-SNAPSHOT` appears stale
- **THEN** the dependency guidance explains the default cache TTL, distinguishes online refresh from `--offline` cache-only resolution, and directs the agent to use `--refresh-dependencies` for an intentional freshness check

#### Scenario: Pivot from graph to source
- **WHEN** an agent needs to understand the API or implementation behind a resolved dependency or Gradle behavior
- **THEN** it stays within `using-gradle` to search and read the relevant dependency, plugin, Gradle, or JDK sources with the appropriate scope

### Requirement: Gradle Internals Access
The skill MUST provide guidance for researching Gradle documentation, release notes, public APIs, and Gradle-own internal source when operating or diagnosing an existing build.

#### Scenario: Investigate Gradle internals
- **WHEN** an agent is troubleshooting a complex build failure and needs phase-ordering or internal API behavior
- **THEN** it consults version-scoped official documentation and uses the Gradle-own-source option with the dependency-source tools rather than guessing from a generic API name

#### Scenario: Preserve research scope
- **WHEN** an agent researches a version-sensitive Gradle or JDK behavior
- **THEN** it reads the wrapper version first, scopes the documentation lookup to that version, and uses source inspection only when official documentation does not establish the required behavior

### Requirement: Orientation in Body
The `SKILL.md` body MUST provide enough guidance for an agent to orient in an unfamiliar existing build without loading a reference, including wrapper-version inspection, project mapping, task discovery, property inspection, common entry points, and the operation-versus-authoring boundary.

#### Scenario: Start in an unfamiliar build
- **WHEN** an agent receives an unfamiliar Gradle project without a loaded reference
- **THEN** it can recognize the wrapper, settings, build-script, source, properties, and catalog markers, read the settings file before interpreting project paths, and load `using-gradle/references/build-orientation.md` for the Basics project/filesystem model
- **AND** it can run the project hierarchy and task-discovery entry points, inspect properties, and identify the next build or test task to execute
- **AND** it can recognize that plugin, repository, module, toolchain, publishing, CI, compiler-option, and testing-framework changes belong to `authoring-gradle-builds`

### Requirement: Version-Aware Guidance
The skill MUST mark version-dependent advice inline for Gradle 7, 8, and 9, provide compatibility-safe fallbacks, bias guidance toward the latest supported wrapper version, and include a compact compatibility quick-reference.

#### Scenario: Choose a version-safe operation
- **WHEN** an agent works across Gradle 7, 8, and 9
- **THEN** it selects the latest-version path when possible and uses documented fallbacks for `--rerun`, version catalogs, configuration-cache maturity, `properties --property`, toolchain provisioning, and daemon controls when the wrapper is older or uncertain

#### Scenario: Separate JVM roles
- **WHEN** an agent diagnoses a JVM or toolchain failure
- **THEN** it distinguishes the JVM running Gradle from the project compile/test toolchain and test workers, and selects `JAVA_HOME` from the wrapper compatibility matrix rather than from source compatibility

#### Scenario: Diagnose stateful environment behavior
- **WHEN** an agent compares runs that differ in daemon, wrapper, or cache state
- **THEN** it records `GRADLE_USER_HOME`, respects same-version daemon scope for `--status` and `--stop`, checks wrapper distribution checksums, and does not bypass checksum verification

#### Scenario: Handle policy-sensitive diagnostics
- **WHEN** an agent considers `--scan` or stricter warning handling
- **THEN** it is warned that build scans publish build metadata and may require terms acceptance, and that `--warning-mode=fail` is an intentional migration gate rather than a default diagnostic setting

### Requirement: Progressive Disclosure
The skill MUST implement a root-local reference system where detailed procedures are stored in task-shaped files and loaded only for the corresponding operating or research task, while keeping `SKILL.md` within its compact body budget.

#### Scenario: Load a focused procedure
- **WHEN** an agent identifies a test, execution, troubleshooting, dependency, or source-research task
- **THEN** it loads only the corresponding reference file and follows its MCP workflow, anti-patterns, version notes, and cross-references

#### Scenario: Keep operational detail discoverable
- **WHEN** an agent needs a gotcha or a source for a recommendation
- **THEN** the local references provide the relevant details for task outcomes, test zero-match and KMP or Android task selection, configuration-cache reuse diagnostics, dependency cache and provenance behavior, daemon and wrapper state, and official documentation or MCP-tool links without requiring speculative navigation

### Requirement: Authoritative Documentation Routing
The skill MUST route agents from every major operating topic to the authoritative version-scoped Gradle documentation exclusively through the `gradle_docs` tool, using only verified tag and path hints and never fabricated tool names. The skill MUST NOT embed published `docs.gradle.org` URLs or `gradle-mcp.rnett.dev` tool-documentation pointers as documentation citations; the `gradle_docs` hint is the single routing mechanism.

#### Scenario: Research a major Gradle topic
- **WHEN** an agent needs authoritative guidance on execution, testing, troubleshooting, dependencies, compatibility, caching, task selection, wrapper integrity, or deprecation behavior
- **THEN** the relevant local reference provides a version-scoped `gradle_docs` tag and path hint (resolved to the project's Gradle version) and routes exclusively through `gradle_docs`, with no published `docs.gradle.org` URL and no `gradle-mcp.rnett.dev` pointer
- **AND** the agent reads `gradle/wrapper/gradle-wrapper.properties` before applying version-sensitive advice

#### Scenario: Override the documentation version
- **WHEN** an agent researches a Gradle version that differs from the project's wrapper (for example a migration target or a specific minor release being verified for a bug fix)
- **THEN** the research guidance directs the agent to pass an explicit `version="X.Y"` to `gradle_docs`, and otherwise to omit `version` so it resolves to the detected wrapper
- **AND** the guidance warns that a coarse version such as `"8"` fails and that silently using the latest release when the wrapper is older is incorrect

#### Scenario: Escalate a documentation lookup
- **WHEN** an agent's first `gradle_docs` search is too narrow or returns nothing
- **THEN** the research guidance provides a lookup ladder — scoped `tag:<tag> <term>` search, then broaden by dropping the tag, then browse the tree with `path="."`, then read a specific `path` — and notes that the no-argument call lists available sections

#### Scenario: Follow cross-topic references
- **WHEN** an agent moves between execution, testing, troubleshooting, dependency inspection, and research
- **THEN** the references preserve local cross-links and the authoritative `gradle_docs` tag and path hints remain available at the topic's procedure home

### Requirement: Operating Environment Coverage
The skill MUST provide substantive, version-aware coverage for Gradle command-line operation, daemon usage, project directory layout, and build-environment configuration through its local references.

#### Scenario: Diagnose an operating environment
- **WHEN** an agent compares runs or diagnoses configuration or process behavior
- **THEN** it loads the relevant local guidance for command-line options, always using the daemon, directory layout, and build-environment configuration, with authoritative Gradle documentation and MCP-tool pointers

