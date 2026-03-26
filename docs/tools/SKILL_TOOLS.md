[//]: # (@formatter:off)

# Skill Tools

Tools for managing Gradle MCP skills.

## install_gradle_skills

Installs or updates the official Gradle MCP skills into the agent's skill directory; provides expert-level workflows for Gradle builds, tests, and dependency management.

Provide the absolute path to your agent's skill directory (e.g., `~/.agents/skills`). Skills are extracted as `SKILL.md` files into named subdirectories.
Set `replaceOld=true` (default) to replace existing skills from this server and ensure the latest guidance is active.

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
      "description": "Replaces existing skills from this MCP server in the target directory."
    }
  },
  "required": [
    "directory"
  ],
  "type": "object"
}
```


</details>




