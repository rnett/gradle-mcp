[//]: # (@formatter:off)

# Skill Tools

Tools for managing Gradle MCP skills.

## install_gradle_skills

ALWAYS use this tool to install or update the official Gradle MCP skills into your agent's skill directory.
These skills provide expert-level workflows, specialized instructions, and deep diagnostic patterns that are essential for mastering Gradle tasks.
It automatically unpacks and configures the latest skills safely.

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




