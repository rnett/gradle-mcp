## Context

When the MCP executes a Gradle build with `publishScan = true`, it currently parses the build output stream for the Terms of Service prompt from the Develocity plugin, automatically replies "yes", and then parses the output for the
published scan URL. This approach is fragile: it depends on standard IO interceptors (`stdoutLineHandler`), complex asynchronous prompting (`tosAccepter`), and regular expressions that could break if Gradle or the Develocity plugin changes
their output format. Gradle supports init scripts to automatically configure plugins and conventions, including accepting TOS automatically.

## Goals / Non-Goals

**Goals:**

- Remove the interactive terminal stream parsing logic used to accept the Gradle Build Scan TOS.
- Create a Gradle init script that automatically applies the Develocity plugin and sets it to accept the TOS.
- Retrieve the published build scan URL safely and reliably via standard Gradle mechanisms or structured logging rather than scraping stdout.

**Non-Goals:**

- Changing how other parameters or background execution are handled in the `gradle` execution tool.
- Upgrading or changing the version of the Develocity plugin beyond what is necessary to apply the script.

## Decisions

- **Use an Init Script**: We will inject a new init script (`scans.init.gradle.kts`) when `publishScan = true` or the arguments contain `--scan`. The script will apply and configure the Develocity plugin or the legacy Gradle Enterprise
  plugin to automatically accept the terms of service (`termsOfUseAgree = "yes"`). The script must be backwards compatible with older versions of the DV/GE plugins. Critically, if a DV/GE plugin is already applied/configured in the build,
  the script should gracefully do nothing to avoid conflicts.
    - *Alternative considered*: Passing `-Dscan.termsOfServiceUrl=... -Dscan.termsOfServiceAgree=yes`. This still works but the init script gives us more control over plugin application if it's missing from the project, and we can configure
      explicit outputs. We will follow the approach linked in the roadmap: https://github.com/gradle/gradle/issues/26316#issuecomment-1739245349, ensuring it works for both `develocity` and `gradleEnterprise` extensions.
- **Scan URL Capture**: The `BuildScanConfiguration` allows setting an `buildScanPublished { ... }` action which we can use to print a specific, easily-parsable line (e.g. `[MCP-BUILD-SCAN] <url>`) to stdout. The MCP will only parse for
  this explicit marker, ignoring arbitrary console output.

## Risks / Trade-offs

- **Risk**: Projects using an older or different build scan plugin might conflict with the injected init script. -> *Mitigation*: Ensure the init script safely applies configuration only if the plugin is present, or conditionally applies a
  compatible plugin version.
- **Risk**: The init script could fail to apply if Develocity plugin is already applied differently. -> *Mitigation*: The linked issue comment shows a robust way to configure `develocity` or `gradleEnterprise` extensions dynamically across
  different Gradle versions.
