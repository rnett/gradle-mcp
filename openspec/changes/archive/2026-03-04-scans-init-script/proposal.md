## Why

Currently, when publishing a build scan, the MCP interacts with the Gradle build process to parse output looking for the scan URL or TOS prompt. This is brittle, depends on specific logging formats, and requires complex interaction logic (
the `tosAccepter`). We can remove this complex interaction logic by using a Gradle init script to automatically apply the build scan plugin and configure it to accept the terms of service automatically when scan publishing is requested.

## What Changes

- Create a new Gradle init script (`scans.init.gradle.kts`) that automatically accepts the terms of service for build scans and captures the scan URL.
- **BREAKING**: Remove the `GradleScanTosAcceptRequest` and `onScansTosRequest` interaction from the `GradleProvider` and related interceptors.
- Update `gradle` tool execution logic to append the `scans.init.gradle.kts` file as an init script when `publishScan` is true.
- Modify the way the build scan URL is captured, relying on the init script output rather than regex matching the general console output for TOS prompts.

## Capabilities

### New Capabilities

- `scans-init-script`: Applying build scan configuration via an init script instead of interactive stream parsing.

### Modified Capabilities

<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->

## Impact

- `dev.rnett.gradle.mcp.gradle.GradleProvider` - Removed `tosAccepter` and related interaction callbacks.
- `dev.rnett.gradle.mcp.gradle.build.GradleBuildOutInterpreter` - Removed parsing logic for TOS prompts.
- Addition of `src/main/resources/init-scripts/scans.init.gradle.kts`.
