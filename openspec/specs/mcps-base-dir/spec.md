# Capability: mcps-base-dir

## Purpose

Defines the default working and log directories for the MCP server, following the `~/.mcps` convention for co-locating MCP server data.

## Requirements

### Requirement: Default working directory follows ~/.mcps convention

The server SHALL use `~/.mcps/rnett-gradle-mcp` as the default working directory when the `GRADLE_MCP_WORKING_DIR` environment variable is not set.

#### Scenario: Default path used when env var is absent

- **WHEN** the server starts without `GRADLE_MCP_WORKING_DIR` set
- **THEN** the working directory SHALL resolve to `<user.home>/.mcps/rnett-gradle-mcp`

#### Scenario: Env var override still respected

- **WHEN** the server starts with `GRADLE_MCP_WORKING_DIR` set to a custom path
- **THEN** the working directory SHALL use the value of `GRADLE_MCP_WORKING_DIR` instead of the default

### Requirement: Default log directory follows ~/.mcps convention

The server SHALL use `~/.mcps/rnett-gradle-mcp/logs` as the default log directory when the `GRADLE_MCP_LOG_DIR` environment variable is not set.

#### Scenario: Default log path used when env var is absent

- **WHEN** the server starts without `GRADLE_MCP_LOG_DIR` set
- **THEN** log files SHALL be written to `<user.home>/.mcps/rnett-gradle-mcp/logs/`

#### Scenario: Log env var override still respected

- **WHEN** the server starts with `GRADLE_MCP_LOG_DIR` set to a custom path
- **THEN** log files SHALL be written to the directory specified by `GRADLE_MCP_LOG_DIR`
