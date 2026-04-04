## Context

The server stores all persistent state (CAS, session views, logs) under a single root directory. This root is currently resolved in two places:

1. `GradleMcpEnvironment.fromEnv()` in `Utils.kt` — defaults to `${user.home}/.gradle-mcp`
2. `logback.xml` — defaults to `${user.home}/.gradle-mcp/logs` via `${GRADLE_MCP_LOG_DIR:-${user.home}/.gradle-mcp/logs}`

The `~/.gradle-mcp` directory does not follow any shared convention for MCP server data. The `~/.mcps/<server-name>` layout is the emerging convention used across multiple MCP servers on this machine.

## Goals / Non-Goals

**Goals:**

- Change the fallback default working directory to `~/.mcps/rnett-gradle-mcp`
- Change the fallback default log directory to `~/.mcps/rnett-gradle-mcp/logs`
- Update all user-facing documentation to reflect the new paths
- Preserve `GRADLE_MCP_WORKING_DIR` and `GRADLE_MCP_LOG_DIR` env overrides so existing installs can opt out

**Non-Goals:**

- Automatic migration of data from `~/.gradle-mcp` to the new location
- Any changes to internal directory structure beneath the root
- New env vars or configuration mechanisms

## Decisions

### 1. Update the default in `GradleMcpEnvironment.fromEnv()`

Change the fallback from `.gradle-mcp` to `.mcps/rnett-gradle-mcp`:

```kotlin
val workingDir = System.getenv("GRADLE_MCP_WORKING_DIR")
    ?: "${System.getProperty("user.home")}/.mcps/rnett-gradle-mcp"
```

**Rationale**: Minimal, single-line change in the only place the default is defined.

### 2. Update `logback.xml` default log path

Change:

```xml
<file>${GRADLE_MCP_LOG_DIR:-${user.home}/.gradle-mcp/logs}/gradle-mcp.log</file>
```

to:

```xml
<file>${GRADLE_MCP_LOG_DIR:-${user.home}/.mcps/rnett-gradle-mcp/logs}/gradle-mcp.log</file>
```

(same change in the `<fileNamePattern>` element)

**Rationale**: Keeps the log directory co-located with the rest of the server data by default.

### 3. Update documentation strings

Replace every occurrence of `~/.gradle-mcp` with `~/.mcps/rnett-gradle-mcp` in `README.md`, `docs/index.md`, and `CONTRIBUTING.md`.

## Risks / Trade-offs

- **[Risk] Existing installs lose data visibility** → Mitigation: `GRADLE_MCP_WORKING_DIR` override is documented; users can point back to `~/.gradle-mcp`.
- **[Risk] Deeper path on Windows** → Adding `.mcps/rnett-gradle-mcp` adds ~22 chars. Well within MAX_PATH for normal cache paths.

## Migration Plan

No automated migration. Users with existing data at `~/.gradle-mcp`:

1. Set `GRADLE_MCP_WORKING_DIR=~/.gradle-mcp` in their MCP server config to keep using the old path, **or**
2. Move `~/.gradle-mcp` to `~/.mcps/rnett-gradle-mcp` manually

On first run with the new default, the new directory is created automatically (existing `cacheDir.mkdirs()` logic in `GradleMcpEnvironment.init`).
