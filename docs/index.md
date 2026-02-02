[![Maven Central Version](https://img.shields.io/maven-central/v/dev.rnett.gradle-mcp/gradle-mcp?style=for-the-badge)](https://central.sonatype.com/artifact/dev.rnett.gradle-mcp/gradle-mcp)
![Maven snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Frnett%2Fgradle-mcp%2Fgradle-mcp%2Fmaven-metadata.xml&strategy=latestProperty&style=for-the-badge&label=SNAPSHOT&color=yellow)
[![GitHub Repo](https://img.shields.io/badge/github-Repo-181717?style=for-the-badge&logo=github)](https://github.com/rnett/gradle-mcp)
[![GitHub License](https://img.shields.io/github/license/rnett/gradle-mcp?style=for-the-badge)](./LICENSE)

# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.

##### Features

* Token-efficient context use. Tools use token efficient formats (i.e. not JSON) and only include the minimum relevant information. Details are relegated to specialized lookup tools.
* Supports publishing Develocity Build Scans, using elicitation to get permission to publish to [the public instance](https://scans.gradle.com).
* Tools for running and managing Gradle builds in the background. Helpful for running dev servers, etc.
* Customization of JVM args, environment variables, and system properties. Plus, the ability to source environment variables from the shell instead of inheriting them - useful on macOS where IntelliJ or Gradle may not start with the right
  env vars.

## Installation

[//]: # (@formatter:off)
!!! warning "JDK Requirement"
    JDK 17 or higher is required to run `gradle-mcp`.
    You can use JBang to install JDKs too: [docs](https://www.jbang.dev/documentation/jbang/latest/javaversions.html).

[//]: # (@formatter:on)

Use [jbang](https://www.jbang.dev/documentation/jbang/latest/installation.html):

```shell
jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

For snapshots:

```shell
jbang run --fresh \
  --repos snapshots=https://central.sonatype.com/repository/maven-snapshots/ \
  dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

You can add an alias to make invoking it easier:

```shell
jbang alias add dev.rnett.gradle-mcp:gradle-mcp:+
```

Then run it with `jbang --fresh gradle-mcp stdio`.

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

```json
{
  "mcpServers": {
    "gradle": {
      "command": "bash -c",
      "args": [
        "jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+ stdio"
      ]
    }
  }
}
```

## Usage

Run the server.
It accepts a single argument, `stdio`, to run in STDIO mode.
By default it runs as a server on port 47813.

[//]: # (@formatter:off)
!!! danger
    **DO NOT EVER EXPOSE THIS SERVER TO THE INTERNET.**
[//]: # (@formatter:on)

## Publishing Build Scans

Even if you don't have your build configured to publish build scans automatically, you can still publish build scans - just ask your agent to publish a scan when invoking Gradle.
These will publish to the public https://scans.gradle.com instance unless you have a Develocity instance configured in your build.
You will be prompted by your agent to accept the terms of service for publishing scans.
If your agent does not support elicitation, you will not be able to publish scans.
