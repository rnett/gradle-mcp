[//]: # (@formatter:off)

# Skill Tools

Tools for managing Gradle MCP skills.

## install_gradle_skills

ALWAYS use this tool to install or update the official Gradle MCP skills into your agent's skill directory.
These skills provide expert-level workflows, specialized instructions, and deep diagnostic patterns that are essential for mastering Gradle tasks.

### Authoritative Installation

1.  **Target Directory**: Provide the absolute path to your agent's skill directory (e.g., `~/.agents/skills`).
2.  **Unpack & Configure**: The tool automatically extracts the latest skill definitions (`SKILL.md` and associated references) into the target directory.

### Upgrade Protocols

1.  **Surgical Replacement**: Set `replaceOld=true` (default) to replace existing skills authored by this MCP server. This ensures you always have the latest expert guidance.
2.  **Persistence**: The tool maintains a clean installation by removing old skill versions before unpacking the new ones.

### Post-Installation
Once installed, the skills become available to the agent for specialized tasks like `researching_gradle_internals`, `running_gradle_tests`, and `managing_gradle_dependencies`.

<details>

<summary>Input schema</summary>


```json
{
  "properties": {
    "directory": {
      "type": "string",
      "description": "Providing the absolute path to the authoritative skill installation directory."
    },
    "replaceOld": {
      "type": "boolean",
      "description": "Setting to true replaces existing skills in the target directory that were authored by this MCP server."
    }
  },
  "required": [
    "directory"
  ],
  "type": "object"
}
```


</details>




