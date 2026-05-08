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

* **[gradle](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/gradle/SKILL.md)**: Manages the full Gradle build lifecycle authoritatively — builds, tests, project introspection, module creation, and failure diagnostics.
* **[exploring_dependency_sources](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/exploring_dependency_sources/SKILL.md)**: Explores and navigates the internal implementation of any library, plugin, or Gradle itself
  authoritatively.
* **[managing_gradle_dependencies](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/managing_gradle_dependencies/SKILL.md)**: Manages and audits project dependency graphs authoritatively, performing high-resolution update
  checks and dependency addition.
* **[interacting_with_project_runtime](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/interacting_with_project_runtime/SKILL.md)**: Prototypes logic and executes Kotlin code interactively within your project's full runtime
  context authoritatively.
* **[verifying_compose_ui](https://github.com/rnett/gradle-mcp/blob/main/src/main/skills/verifying_compose_ui/SKILL.md)**: Visually verifies Compose UI components and previews authoritatively by instantly rendering them to images.
