# Capability: scans-init-script

## Purpose

Specifies how the MCP server applies a Develocity/Gradle Enterprise init script for build scans when explicitly requested, with automatic TOS acceptance and scan URL capturing.

## Requirements

### Requirement: Init script applied when explicitly requested

When the `gradle` execution tool is invoked with `publishScan = true` or the `--scan` argument is present in the `commandLine`, the MCP SHALL append an initialization script (`scans.init.gradle.kts`) to the Gradle arguments.

#### Scenario: User requests a build scan via publishScan parameter

- **WHEN** the `gradle` tool is called with `publishScan: true`
- **THEN** the MCP includes `--init-script` and the path to the internal scans init script in the Gradle invocation arguments.

#### Scenario: User requests a build scan via CLI args

- **WHEN** the `gradle` tool is called with `--scan` in the `commandLine` arguments
- **THEN** the MCP includes `--init-script` and the path to the internal scans init script in the Gradle invocation arguments.

### Requirement: Automatic TOS acceptance and Plugin Compatibility

The `scans.init.gradle.kts` script SHALL automatically configure the Develocity or Gradle Enterprise plugin to accept the Terms of Service. The script MUST be compatible with older versions of the Gradle Enterprise (`com.gradle.enterprise`)
plugin as well as newer Develocity plugins. If the build already has a DV/GE plugin applied and configured, the script SHALL gracefully do nothing to avoid configuration conflicts.

#### Scenario: Build scan runs with newer Develocity plugin

- **WHEN** a build uses the `com.gradle.develocity` plugin
- **THEN** the TOS is automatically accepted.

#### Scenario: Build scan runs with older Gradle Enterprise plugin

- **WHEN** a build uses the older `com.gradle.enterprise` plugin
- **THEN** the TOS is automatically accepted without errors.

#### Scenario: Build scan runs with already configured plugin

- **WHEN** a build already has a DV/GE plugin configured
- **THEN** the init script does nothing and does not overwrite existing configuration.

### Requirement: Reliable scan URL capturing

The system SHALL capture the build scan URL output by the init script and add it to the `runningBuild.publishedScansInternal`.

#### Scenario: Build scan is successfully published

- **WHEN** the build completes and publishes a scan
- **THEN** the `publishedScansInternal` list includes the URL parsed from the deterministic output marker produced by the init script.
