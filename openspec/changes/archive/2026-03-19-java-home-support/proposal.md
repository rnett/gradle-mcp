# Proposal: Explicit Java Home Support for Gradle Builds

## Summary

Add support for an explicit `javaHome` parameter in Gradle invocation arguments to allow running builds with a specific JDK version.

## Problem

Currently, the Gradle MCP server uses the default JVM environment for running builds. This fails in scenarios where a specific JDK version is required but not automatically detected or correctly provisioned by Gradle toolchains (e.g., when
running specialized plugins like Dokka that have strict JVM version requirements).

## Solution

1. Add a `javaHome` field to `GradleInvocationArguments`.
2. Update `BuildExecutionService` to use the `javaHome` parameter when configuring the Gradle Tooling API `ConfigurableLauncher`.
3. Provide descriptive warnings if the specified `javaHome` does not exist.

## Impact

- Better reliability when running Gradle builds that require specific JDK versions.
- Enables workaround for JVM version detection issues in third-party plugins.
- More flexible build configuration for complex Gradle projects.
