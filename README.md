[![Maven Central Version](https://img.shields.io/maven-central/v/dev.rnett.gradle-mcp/gradle-mcp?style=for-the-badge)](https://central.sonatype.com/artifact/dev.rnett.gradle-mcp/gradle-mcp)
[![Maven snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Frnett%2Fgradle-mcp%2Fgradle-mcp%2Fmaven-metadata.xml&strategy=latestProperty&style=for-the-badge&label=SNAPSHOT&color=yellow)]()
[![Documentation](https://img.shields.io/badge/Documentation-2C3E50?style=for-the-badge&logo=googledocs&logoColor=D7DBDD)](https://gradle-mcp.rnett.dev/latest/)
[![GitHub License](https://img.shields.io/github/license/rnett/gradle-mcp?style=for-the-badge)](./LICENSE)

# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.

##### Features

* Token-efficient context use. Tools use token efficient formats (i.e. not JSON) and only include the minimum relevant information. Details are relegated to specialized lookup tools.
* Access to full test output, which is not typically possible when running from a terminal.
* Supports publishing Develocity Build Scans, using elicitation to get permission to publish to [the public instance](https://scans.gradle.com).
* Tools for running and managing Gradle builds in the background. Helpful for running dev servers, etc.
* Customization of JVM args, environment variables, and system properties. Plus, the ability to source environment variables from the shell instead of inheriting them - useful on macOS where IntelliJ or Gradle may not start with the right
  env vars.

### Configuration

The `GRADLE_MCP_PROJECT_ROOT` environment variable can be set to provide a default Gradle project root. This is used if no project root is specified in a tool call and there isn't exactly one MCP root configured.

## Getting started

> [!IMPORTANT]
> JDK 17 or higher is required to run `gradle-mcp`.
> You can use JBang to install JDKs too: [docs](https://www.jbang.dev/documentation/jbang/latest/javaversions.html).

Use [jbang](https://www.jbang.dev/documentation/jbang/latest/installation.html):

```shell
jbang run --fresh dev.rnett.gradle-mcp:gradle-mcp:+ stdio
```

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

See the [documentation](https://gradle-mcp.rnett.dev/latest/) for more details.