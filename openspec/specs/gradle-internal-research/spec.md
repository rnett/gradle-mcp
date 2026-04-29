# Capability: gradle-internal-research

## Purpose

Provides clear instructions for researching Gradle internals, official documentation, and plugin sources using specialized MCP tools, with resolved concrete version strings.

## Requirements

### Requirement: Resolved versions for source research

The system SHALL use concrete versions resolved from aliases when searching for or reading source code for a specific version of Gradle.

#### Scenario: Research internal APIs with "current"

- **WHEN** the user researches internal APIs for version `"current"`
- **THEN** the system SHALL resolve `"current"` to a concrete version (e.g., `"8.6.1"`) and use that version for all source-level research cache directories (e.g., `.../8.6.1/`)

### Requirement: Authoritative Gradle internal and documentation lookup

The `gradle_expert` skill SHALL provide clear instructions for researching Gradle's internals and official documentation using specialized tools.

#### Scenario: Researching a Gradle internal API

- **WHEN** user asks how a specific internal Gradle feature works (e.g., how `Property` is implemented)
- **THEN** system SHALL use `search_dependency_sources` with `gradleSource: true` to find the relevant classes and then `read_dependency_sources` to examine the implementation.

#### Scenario: Looking up official documentation

- **WHEN** user asks for the official documentation for a specific task or plugin (e.g., `JacocoReport`)
- **THEN** system SHALL use `gradle_docs` with a specific query or path to retrieve the official guide or DSL reference.

#### Scenario: Searching for official samples

- **WHEN** user asks for code samples for a specific Gradle feature (e.g., "how to use the toolchains API")
- **THEN** system SHALL use `gradle_docs` with `query: "tag:samples <feature>"` to find and then read the relevant sample implementation.

#### Scenario: Searching the Java API Reference (Javadocs)

- **WHEN** user asks for detailed class or method information from the Gradle API (e.g., "what are the methods on Project?")
- **THEN** system SHALL use `gradle_docs` with `query: "tag:javadoc <class-name>"` to retrieve the Javadoc for that class.

## Design & Rationale

### Gradle Source Organization

The Gradle codebase is primarily divided into:

- `subprojects/`: Core Gradle engine and base APIs (e.g., `core-api`, `core`).
- `platforms/`: Functional groups of modules building on core (e.g., `dependency-management`, `kotlin-dsl`, `jvm`).

### Key Locations for Research

- **Public APIs**: `subprojects/core-api` (e.g., `Project.java`, `Task.java`).
- **Internal Implementations**: `subprojects/core` (e.g., `DefaultProject.java`).
- **Dependency Management**: `platforms/software/dependency-management`.
- **Kotlin DSL**: `platforms/core-configuration/kotlin-dsl`.
- **Standard Plugins**: Distributed under `platforms/` (e.g., `jvm/plugins-java`, `core-configuration/base-diagnostics`).
- **Execution & Workers**: `platforms/core-execution/`.
- **Tooling API**: `platforms/ide/tooling-api/`.

