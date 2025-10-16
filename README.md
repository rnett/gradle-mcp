[![Maven Central Version](https://img.shields.io/maven-central/v/dev.rnett.gradle-mcp/gradle-mcp?style=for-the-badge)](https://central.sonatype.com/artifact/dev.rnett.gradle-mcp/gradle-mcp)
![Maven snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Frnett%2Fgradle-mcp%2Fgradle-mcp%2Fmaven-metadata.xml&strategy=latestProperty&style=for-the-badge&label=SNAPSHOT&color=yellow)
[![GitHub License](https://img.shields.io/github/license/rnett/gradle-mcp?style=for-the-badge)](./LICENSE)

# Gradle MCP server

A MCP server for Gradle.
Tools include introspecting projects, running tasks, and running tests.
Also supports publishing Develocity Build Scans.

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

See the [documentation](gradle-mcp.rnett.dev) for more details.