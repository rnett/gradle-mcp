# Capability: interacting-with-project-runtime

## Description
Provides a persistent JVM/Kotlin REPL for executing and probing project logic.

### Use when
- Verifying dynamic behavior of a class or function in the project runtime.
- Probing internal state or executing experimental logic without a full build cycle.
- Rapidly prototyping logic changes within the JVM classpath.

### Do NOT use
- Operating a Gradle build (use `using-gradle`).
- Modifying build definitions (use `authoring-gradle-builds`).
- Rendering Compose UI components (use `verifying-compose-ui`).

## Purpose

This capability defines the workflows and requirements for project runtime interaction.

## Requirements

### Requirement: Compact Capability Body
MUST preserve the REPL lifecycle and source-vs-runtime decision semantics within a compact, single-file capability body.

#### Scenario:
An agent needs to verify a complex logic change; it uses the `interacting-with-project-runtime` skill to start a session, execute the target function, and inspect the return value without needing a full build-and-run cycle.

### Requirement: Shared Setup Materialization
MUST implement the shared setup plumbing as an `authored-shared` resource, materialized into this skill and `verifying-compose-ui`.

#### Scenario:
The REPL requires a specific JVM setup (e.g. incubator modules); the agent uses the materialized shared setup reference to ensure the environment is correctly initialized before running codeS.
