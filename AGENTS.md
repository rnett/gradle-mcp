# Gradle MCP Project Memory

This repository contains a Model Context Protocol (MCP) server written in Kotlin, designed to provide LLMs with deep, programmatic access to Gradle build systems. It serves as both a tool provider and a laboratory for agentic Gradle
workflows.

## Primacy Zone: Fundamental Mandates

1. **Security & Secrets**: NEVER log, print, or commit API keys, secrets, or .env contents.
2. **Source Control**: ALWAYS add changes to Git for persistence, but NEVER create commits or push unless explicitly instructed.
3. **Tool Preference**: ALWAYS prefer using the Gradle MCP tools (`gradle`, `inspect_build`, etc.) over raw shell execution of `./gradlew` or generic shell commands.
4. **Verification**: NO change is complete without passing relevant tests and the final `check` task.
5. **Tool Metadata**: After modifying any tool descriptions or metadata, you MUST run `./gradlew :updateToolsList` to ensure consistency.
6. **Agent Documentation**: This is an MCP server and set of tools for agents to use. If behavior or features aren't documented in the tool descriptions AND skills, they might as well not exist. Ensure they are documented well enough in
   both places for agents to use them.

---

## WHAT (Architecture & Terminology)

### Core Components

- **MCP Server**: The main entry point using the Model Context Protocol Kotlin SDK.
- **Gradle Provider**: The internal engine that orchestrates Gradle Tooling API calls and manages background builds.
- **Repl Worker**: A separate process (`repl-worker` module) that executes Kotlin snippets for prototyping and UI verification.
- **Skills**: Specialized agentic workflows stored in `./skills/`. These are "agentic documentation" that guide Gemini on how to solve complex Gradle tasks. These skills are designed to be generic and portable, intended for installation by
  any user of this Gradle MCP server. They must avoid project-specific hardcoding and focus on idiomatic Gradle usage across diverse environments.

### Key Terms

- **BuildId**: A unique UUID used to track and monitor background builds.
- **Task Output Capturing**: A mechanism to isolate the console output of a specific task, reducing context noise.
- **ToolNames**: The source of truth for all MCP tool identifiers (see `src/main/kotlin/dev/rnett/gradle/mcp/tools/ToolNames.kt`).

---

## WHY (Architectural Rationale)

### Kotlin & Koin

- **Why Kotlin?**: To leverage Gradle's native type system and the rich ecosystem of JVM-based build tools.
- **Why Isolated Koin?**: To prevent global state leakage between tool calls and ensure deterministic behavior in the MCP server's request-response lifecycle.

### Testing & Assertions

- **Why Power Assert?**: It eliminates the need for complex assertion libraries by providing rich, contextual failure messages from simple `assert` calls.
- **Why Class-Level Test Resources?**: Creating Gradle projects and daemon environments is expensive; we reuse them across tests to maintain a fast feedback loop.
- **Rule**: NEVER use reflection hacks for tests. ALWAYS close and clean up any resources or services they create.

### MCP Design

- **Why Text over JSON?**: LLMs process natural language better than raw JSON. Tools should return structured, human-readable text (often Markdown) to maximize reasoning.
- **Why Tooling API?**: It is the official, supported way to interact with Gradle programmatically, providing better stability than parsing CLI output.
- **MCPs vs Skills**: While using our skills in `./skills` should be encouraged as the primary endpoint, the MCP must work without the tools. This means that the MCP tool descriptions must contain enough information to use the without
  relying on the skills.

---

## HOW (Operational Execution)

### Build & Test Commands

- **Build All**: `./gradlew build`
- **Run Tests**: `./gradlew test`
- **Quality Check**: `./gradlew check` (Runs all verification tasks including linting and tests)
- **Update Tools**: `./gradlew :updateToolsList` (Mandatory after metadata changes)

### Research & Problem Solving

- **Research First**: ALWAYS research the correct way to implement a feature or use a library before attempting implementation. Do not rely on trial-and-error for complex APIs like Kotlin Scripting.
- **Rubber Ducking**: If stuck after a few tries, stop and think about the issues before proceeding. Rubber duck to yourself and document your findings.

### Code Style & Conventions

- **Test Naming**: Always name tests using descriptive names in English, wrapped in backticks (e.g., `` `verify build status wait` ``).
- **Concurrency**: ALWAYS use `runTest` for suspending tests, NEVER `runBlocking`.
- **Dependency Catalog**: ALWAYS put dependencies in `gradle/libs.versions.toml`. Every version ref MUST have a corresponding entry for automated updates.
- **Progress Reporting**: ALWAYS send progress commands/notifications (`ProgressReporter`) when implementing long-running operations or tool handlers. Do not omit them to reduce noise; they will be filtered later by the client.

### Skill Development Workflow

Skills in this project guide other agents. They will be installed in users' skill directories as standalone skills. Follow the "Expert Tone" and "Progressive Disclosure" principles.

1. **Scope**: "Update skills" directives ALWAYS refer to skills in `./skills/`, NEVER global skills.
2. **Sync**: When changing a tool, you MUST update all referencing skills in `./skills/` and other dependent tools.
3. **Create**: New directory in `skills/` with `SKILL.md`.
4. **Refine**: Move deep-dives to `references/`.
5. **Register**: Update `docs/skills.md` with the new skill details.
6. **Reference**: Use relative links to living files to avoid "instruction rot."  But you CAN NOT link to library sources from skills, and double check whether cross skill links will work before adding them.
7. **MCP Tooling:** Skills are largely focused around using this server's MCP tools. The skills in a given commit should work with the tools in a given commit - ensure they are consistent. They may not match the names used by the gradle MCP
   server in your current session - that's OK.

---

## Gemini-Specific Optimization

- **Prefix Stability**: This file (`AGENTS.md`) is designed to be at the start of the context. Maintain its structure to maximize context caching hits.
- **Instruction Density**: Focus on non-obvious constraints. If it can be inferred from the file structure, don't add it here.
- **Caching**: This workspace uses heavy caching for Gradle sources. Use `inspect_dependencies` and `search_dependency_sources` with `fresh = true` only when dependencies change.
- **Gradle Source Exploration**: On some machines, the Gradle build tool project is checked out in `./managing-gradle-builds-tool`. Use it as a source of knowledge for Gradle's internal APIs, but be careful not to include it in broad
  search/build commands unless intended. See [gradle-sources-overview.md](./gradle-sources-overview.md).

---

## Resource Index

- **Gradle Sources Navigation**: [gradle-sources-overview.md](./gradle-sources-overview.md)
- **Tool Definitions**: [ToolNames.kt](src/main/kotlin/dev/rnett/gradle/mcp/tools/ToolNames.kt)
- **User/Human Facing Skill Docs**: [docs/skills.md](./docs/skills.md)
