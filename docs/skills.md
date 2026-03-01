# Agent Skills

Agent Skills are markdown files that provide context, instructions, and examples for an AI agent on how to use specific sets of tools effectively. They help the agent understand the best practices, common workflows, and troubleshooting
steps for the tools provided by the Gradle MCP server.

## Included Skills

The following skills are included in the `skills/` directory of the repository:

* **[gradle-build](https://github.com/rnett/gradle-mcp/blob/main/skills/gradle-build/SKILL.md)**: Instructions for running Gradle commands (foreground or background), managing long-running jobs (like dev servers), and investigating build
  failures.
* **[gradle-test](https://github.com/rnett/gradle-mcp/blob/main/skills/gradle-test/SKILL.md)**: Workflows for running tests, filtering tests, and investigating test failures.
* **[gradle-repl](https://github.com/rnett/gradle-mcp/blob/main/skills/gradle-repl/SKILL.md)**: Using the project REPL to run code in the project's environment, inspect state, and test logic.

## How to Include Skills in Your Project

To ensure your AI agent has access to these skills, you should include them in your project's context.

### Recommended: Using Context7's Skills Registry

The easiest and most effective way to include these skills (and keep them up to date) is by using the **[Context7 Skills Registry](https://context7.com/skills)**.
Context7 provides a dedicated registry and CLI for discovering, installing, and managing MCP skills from GitHub repositories.

To install the Gradle MCP skills using the Context7 CLI:

```shell
npx ctx7 skills install /rnett/gradle-mcp --all
```

For more information, see the [Context7 Skills documentation](https://context7.com/docs/skills).

### Manual Inclusion

Alternatively, you can manually include the `SKILL.md` files from the `skills/` directory in your project's documentation or context folder (e.g., `.claudemdc` for Claude or similar directories for other agents).

We recommend pointing your agent to the [GitHub repository](https://github.com/rnett/gradle-mcp/tree/main/skills) if it has web browsing capabilities, or copying the relevant `SKILL.md` files into your project root.

### Future distribution: MCP server

The MCP spec is working on adding support for distributing agent skills as part of an MCP server.
Once that is available, we will use it.