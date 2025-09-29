# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.
Also supports publishing Develocity Build Scans.

## Installation

Use [jbang](https://www.jbang.dev/documentation/jbang/latest/installation.html):

```shell
jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

For snapshots:

```shell
jbang run --fresh --repos snapshots=https://central.sonatype.com/repository/maven-snapshots/ dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

> [!CAUTION]
> For now, you need to use `0.0.1-SNAPSHOT` as the snapshot version since I mistakenly published a few `1.0-SNAPSHOT` versions.
> If you're seeing `Error: Could not find or load main class dev.rnett.gradle.mcp.Application`, this is the cause.


You can add an alias to make invoking it easier:

```shell
jbang alias add dev.rnett.gradle-mcp:gradle-mcp:+
```

Then run it with `jbang gradle-mcp stdio`.

Or even install it as a command (`gradle-mcp`):

```shell
jbang app setup
jbang app install --name gradle-mcp dev.rnett.gradle-mcp:gradle-mcp:+
```

See [jbang documentation](https://www.jbang.dev/documentation/jbang/latest/install.html) for more details.

#### Example MCP configuration

```json
{
  "mcpServers": {
    "gradle": {
      "command": "jbang",
      "args": [
        "run",
        "--fresh",
        "dev.rnett.gradle-mcp:gradle-mcp:+",
        "stdio"
      ]
    }
  }
}
```

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