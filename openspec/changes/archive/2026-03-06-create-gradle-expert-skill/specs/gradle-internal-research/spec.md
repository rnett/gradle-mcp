## ADDED Requirements

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

#### Scenario: Exploring plugin sources

- **WHEN** user wants to understand how a third-party plugin works
- **THEN** system SHALL use `inspect_dependencies` to find the artifact and `read_dependency_sources` to explore its source code.
