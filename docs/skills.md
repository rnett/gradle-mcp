# Agent Skills

<!-- 
NOTE: The descriptions in this file are intended for human users to understand what each skill provides. 
The authoritative, persuasive descriptions used by AI agents are found in the `SKILL.md` files themselves.
-->

Agent Skills are markdown files that provide context, instructions, and examples for an AI agent on how to use specific sets of tools effectively. They help the agent understand the best practices, common workflows, and troubleshooting
steps for the tools provided by the Gradle MCP server.

## How to Include Skills in Your Project

Agent skills should be installed into a directory where your calling agent can find and use them (e.g., its `.claudemdc` directory for Claude, or a specific skills directory).

### Recommended: Using the `install_gradle_skills` Tool

The easiest way to include these skills is by using the `install_gradle_skills` tool provided by this MCP server.
Simply tell your agent to do so.

### Alternative: Using Context7's Skills Registry

If you prefer using an external registry and CLI, you can use the **[Context7 Skills Registry](https://context7.com/skills)**.
Context7 provides a dedicated registry and CLI for discovering, installing, and managing MCP skills from GitHub repositories.

To install the Gradle MCP skills using the Context7 CLI:

```shell
npx ctx7 skills install /rnett/gradle-mcp --all
```

For more information, see the [Context7 Skills documentation](https://context7.com/docs/skills).

### Manual Inclusion

Alternatively, you can manually include the `SKILL.md` files from the `skills/` directory in your project's documentation or context folder (e.g., `.claudemdc` for Claude or similar directories for other agents).

We recommend pointing your agent to the [GitHub repository](https://github.com/rnett/gradle-mcp/tree/main/src/main/skills) if it has web browsing capabilities, or copying the relevant `SKILL.md` files into your project root.

### Future distribution: MCP server

The MCP spec working group is working on adding support for distributing agent skills as part of an MCP server.
Once that is available, we will use it to distribute our skills.

## Included Skills

The following skills are included in the `skills/` directory of the repository:

[//]: # (<<SKILLS_LIST_START>>)

* **[using-gradle](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/using-gradle/SKILL.md)**: Operates existing Gradle builds: orient in the project, run tasks, test, diagnose failures, inspect dependencies, and research Gradle or dependency sources. Trivial everyday dependency edits (version-catalog entries, library declarations, and version bumps) are in scope; structural authoring of plugins, repositories, modules, toolchains, publishing, CI, compiler options, or testing frameworks belongs in authoring-gradle-builds.
* **[authoring-gradle-builds](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/authoring-gradle-builds/SKILL.md)**: Authors and modifies Gradle build definitions, project structure, build logic, and delivery wiring: build lifecycle and Kotlin DSL fundamentals, managed types and lazy Property/Provider configuration, convention and binary plugin development (including TestKit testing and Plugin Portal publishing), Java builds and source sets, configurations and feature variants, dependencies and catalogs, toolchains, Kotlin compiler options, testing-framework configuration, publishing, CI, locking, build scans, Worker API, continuous builds, and configuration-cache/build-cache-safe authoring. Operation/execution (running builds, running tests, diagnosing failures, enabling/persisting the build or configuration cache, and read-only dependency inspection/update discovery) belongs to `using-gradle`; authoring/modifying build definitions belongs to `authoring-gradle-builds`. Trivial one-line everyday dependency edits (catalog entry + declaration + version bump) are a sanctioned overlap in `using-gradle`; anything structural (plugins, repositories, modules, toolchains, publishing, CI) is `authoring-gradle-builds` only. Researching internal Gradle APIs belongs to `using-gradle`'s research workflow.
* **[interacting-with-project-runtime](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/interacting-with-project-runtime/SKILL.md)**: Provides a persistent JVM/Kotlin REPL for executing and probing project logic within the full classpath context.
* **[verifying-compose-ui](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/verifying-compose-ui/SKILL.md)**: Visually verifies Compose UI components and previews by rendering them to images from the JVM runtime.

[//]: # (<<SKILLS_LIST_END>>)
