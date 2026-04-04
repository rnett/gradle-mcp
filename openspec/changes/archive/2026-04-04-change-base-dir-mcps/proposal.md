## Why

The current default working directory (`~/.gradle-mcp`) is a flat, top-level home directory entry that doesn't fit well alongside other MCP servers. Moving it to `~/.mcps/rnett-gradle-mcp` collocates all MCP server data under a shared
`~/.mcps/` namespace, making it easier to manage, back up, and migrate multiple servers.

## What Changes

- Change the default working directory from `~/.gradle-mcp` to `~/.mcps/rnett-gradle-mcp`
- Update `GradleMcpEnvironment.fromEnv()` in `Utils.kt` to use the new path when `GRADLE_MCP_WORKING_DIR` is not set
- Update all documentation references (`README.md`, `docs/index.md`, `CONTRIBUTING.md`) that mention `~/.gradle-mcp`

## Capabilities

### New Capabilities

- `mcps-base-dir`: Default working directory follows the `~/.mcps/<server-name>` convention

### Modified Capabilities

- `cached-source-retrieval`: Path to stored dependency sources changes from `~/.gradle-mcp/cache/…` to `~/.mcps/rnett-gradle-mcp/cache/…`
- `gradle-docs-querying`: Path to Gradle docs cache changes from `~/.gradle-mcp/cache/reading_gradle_docs` to `~/.mcps/rnett-gradle-mcp/cache/reading_gradle_docs`

## Impact

- **Code**: `src/main/kotlin/dev/rnett/gradle/mcp/Utils.kt` — one-line default path change
- **Docs**: `README.md`, `docs/index.md`, `CONTRIBUTING.md` — path string updates
- **Existing installs**: Users with data in `~/.gradle-mcp` will not have their data automatically migrated; they must set `GRADLE_MCP_WORKING_DIR=~/.gradle-mcp` to continue using the old location, or manually move the directory
- **No API/protocol changes**; `GRADLE_MCP_WORKING_DIR` env override continues to work as before
