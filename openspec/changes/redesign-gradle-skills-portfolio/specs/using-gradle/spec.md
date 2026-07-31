# Capability: using-gradle

## Description
Schedules and performs root-level Gradle operations for inspecting and operating existing builds.

### Use when
- Mapping the project hierarchy, discovering runnable tasks, or inspecting project properties.
- Executing Gradle tasks in foreground or background.
- Monitoring build progress or capturing isolated task output.
- Diagnosing build failures through filtered test execution or diagnostic tasks.
- Researching official Gradle documentation, release notes, or internal APIs.
- Auditing the dependency graph, resolving version conflicts, or discovering library updates.
- Searching and reading source code for dependencies, plugins, or Gradle itself.

### Do NOT use
- Modifying build scripts, settings, or module definitions.
- Adding plugins, repositories, or dependency declarations.
- Configuring toolchains, compiler options, or testing frameworks.
- Executing arbitrary Kotlin/Java code via the REPL.
- Rendering Compose UI components.

## ADDED Requirements

### Requirement: Broad Operational Index
MUST provide a workflow index that directs agents to specialized procedures for project mapping, execution, diagnosis, and research, while keeping the default body compact.

#### Scenario:
An agent needs to figure out how to run a specific build task but doesn't know the exact task name; it utilizes the `using-gradle` body to find the project mapping procedure, then loads the corresponding reference to discover the appropriate `gradlew` command.

### Requirement: Dependency and Source Research
MUST consolidate all dependency-graph auditing and source-reading capabilities (formerly `exploring-dependency-sources` and `managing-gradle_dependencies` inspection) into this skill.

#### Scenario:
An agent identifies a version conflict in the dependency tree and needs to read the source code of the conflicting dependency to understand the API change; it stays within `using-gradle` to pivot from auditing to source reading.

### Requirement: Gradle Internals Access
MUST provide guidance on using Gradle MCP tools to research internal APIs and the build lifecycle.

#### Scenario:
An agent is troubleshooting a complex build failure and needs to understand the phase-ordering of a specific internal Gradle plugin; it consults the `gradle-internals.md` reference.

### Requirement: Progressive Disclosure
MUST implement a root-local reference system where detailed procedures are stored in separate files and loaded only upon specific trigger.

#### Scenario:
To keep the main skill body under the 150-line budget, the specific steps for "Filtering Test Execution" are moved to a standalone reference that is only loaded when the agent identifies a test-failure diagnosis task.
