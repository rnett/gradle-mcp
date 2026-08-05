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

* **[using-gradle](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/using-gradle/SKILL.md)**: Using Gradle MCP tools to inspect and run existing builds, including projects, tasks, properties, dependencies, and build results. Activate for Gradle build operation and diagnosis; use authoring-gradle-builds when the build definition itself must change.
* **[authoring-gradle-builds](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/authoring-gradle-builds/SKILL.md)**: Authoring and modifying Gradle build logic, including settings, plugins, dependencies, tasks, conventions, and upgrades. Activate when build files or plugins must change; use using-gradle for inspection or execution without build-definition edits.
* **[advanced-gradle-dependencies](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/advanced-gradle-dependencies/SKILL.md)**: Analyzing advanced Gradle dependency behavior, including resolution, variants, capabilities, conflicts, constraints, and publication metadata. Activate for dependency-resolution design or diagnosis; use authoring-gradle-builds for routine dependency declarations.
* **[interacting-with-project-runtime](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/interacting-with-project-runtime/SKILL.md)**: Interacting with a project's JVM runtime through the Kotlin REPL to execute focused probes against project classes and dependencies. Activate when behavior must be observed by running code; use using-gradle for build inspection or task execution.
* **[verifying-compose-ui](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/verifying-compose-ui/SKILL.md)**: Verifying Compose UI by rendering components or previews and inspecting the resulting images and state transitions. Activate for visual behavior that requires runtime rendering; use ordinary tests for nonvisual logic.

[//]: # (<<SKILLS_LIST_END>>)
