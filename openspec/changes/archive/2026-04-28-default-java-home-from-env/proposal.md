## Why

Gradle builds often fail or behave unexpectedly if the JDK used by the Tooling API doesn't match the environment (e.g., when run via `jbang` which might have its own JDK, or when the shell has a specific `JAVA_HOME` that the host process
doesn't inherit). Currently, if `javaHome` is not explicitly provided in `GradleInvocationArguments`, the Tooling API is left to its default behavior, which may not align with the `JAVA_HOME` environment variable found in the user's shell
or inherited environment.

Defaulting `JAVA_HOME` from the resolved environment (especially when `envSource=SHELL` is used) ensures consistent and predictable build behavior by explicitly telling the Tooling API which JDK to use.

## What Changes

- Modify `BuildExecutionService` to automatically use the `JAVA_HOME` environment variable from the resolved environment (inherited or shell) as the default for `launcher.setJavaHome()` if `args.javaHome` is not explicitly specified.
- Update `GradleInvocationArguments` documentation and metadata to clarify this new default behavior.
- Ensure that if `JAVA_HOME` is not present in the environment and `javaHome` is not provided, the Tooling API still falls back to its default behavior.

## Capabilities

### New Capabilities

- `automatic-java-home-resolution`: Automatically resolving and applying the Java home from the environment if not explicitly provided.

### Modified Capabilities

- `build-execution`: Integrating the automatic Java home resolution into the build execution lifecycle.

## Impact

- `BuildExecutionService`: Implementation logic for Java home resolution.
- `GradleInvocationArguments`: Metadata and documentation for the `javaHome` property.
- Build Reliability: Reduced chance of JDK mismatches, especially in containerized or `jbang`-managed environments.
