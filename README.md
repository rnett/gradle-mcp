# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.
Also supports publishing Develocity Build Scans.

## Installation

### Hacky but convenient

Download the `mcp.init.gradle.kts` file into your `~/.gradle/init.d/` directory.
You can then run the server with `./gradlew --no-daemon gradle-mcp`.
To run a STDIO server, use `./gradlew -q --no-daemon --no-configuration-cache --refresh-dependencies gradle-mcp stdio`.
The server will be stopped if the daemon is killed (e.g. via `./gradlew --stop`).

The latest version will be used automatically. To set a version, set the `gradle.mcp.version` in your `~/.gradle/gradle.properties` file (project properties files will not work).

#### Example MCP configuration

If you're using `gradlew` (as opposed to `gradle`) make sure to set the working directory to a directory with a `gradlew` script.

##### Windows

```yaml
{
  "mcpServers": {
    "gradle": {
      "command": "cmd",
      "args": [
        "/C",
        "gradlew.bat",
        "-q",
        "--no-daemon",
        "--no-configuration-cache",
        "--refresh-dependencies",
        "gradle-mcp",
        "stdio"
      ]
    }
  }
}
```

##### Windows

```yaml
{
  "mcpServers": {
    "gradle": {
      "command": "gradlew",
      "args": [
        "-q",
        "--no-daemon",
        "--no-configuration-cache",
        "--refresh-dependencies",
        "gradle-mcp",
        "stdio"
      ]
    }
  }
}
```

Mac/Linux

### Docker

Coming soon.

## Usage

Run the server.
It accepts a single argument, `stdio`, to run in STDIO mode.
By default it runs as a server on port 47813.

> [!CAUTION]
> **DO NOT EVER EXPOSE THIS SERVER TO THE INTERNET.**

## Publishing Build Scans

Even if you don't have your build configured to publish build scans automatically, you can still publish build scans - just ask your agent to publish a scan when invoking Gradle.
These will publish to the public https://scans.gradle.com instance unless you have a Develocity instance configured in your build.
You will be prompted by your agent to accept the terms of service for publishing scans.
If your agent does not support elicitation, you will not be able to publish scans.