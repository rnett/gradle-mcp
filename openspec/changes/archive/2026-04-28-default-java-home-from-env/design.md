## Context

`GradleInvocationArguments` includes an optional `javaHome` property to specify the JDK used for Gradle builds. Separately, it includes an `envSource` property that determines how environment variables are resolved (INHERIT, NONE, or
SHELL). Currently, `BuildExecutionService` resolves the environment variables and applies them to the launcher, but it only sets the Java home if `args.javaHome` is explicitly provided.

The goal is to use `JAVA_HOME` from the resolved environment as a fallback for `args.javaHome`, providing a more "sensible" default that matches the user's expected environment, especially when using `EnvSource.SHELL`.

## Goals / Non-Goals

**Goals:**

- Automatically use `JAVA_HOME` from the resolved environment (as determined by `envSource`) if `args.javaHome` is not specified.
- Ensure the priority is: Explicit `args.javaHome` > Environment `JAVA_HOME` > Tooling API Default.
- Validate that any resolved Java home exists and is a directory before applying it.
- Update tool metadata to reflect this default behavior.

**Non-Goals:**

- Automatically detecting JDKs beyond what is specified in `JAVA_HOME`.
- Changing how `EnvSource` works.

## Decisions

### 1. Resolution Logic in `BuildExecutionService.configureLauncher`

The fallback logic will be implemented within `BuildExecutionService.configureLauncher`.

- **Rationale**: This is where both the resolved environment (`actualEnvVars`) and the invocation arguments are already present. It allows for a centralized point of configuration for the Gradle launcher.
- **Alternatives considered**: Adding a property to `GradleInvocationArguments`. This was rejected as it would require passing an `EnvProvider` to the data class or making it more complex than necessary.

### 2. Explicit `launcher.setJavaHome()` call

The resolved Java home will be explicitly set on the launcher using `setJavaHome()`.

- **Rationale**: Setting the `JAVA_HOME` environment variable via `setEnvironmentVariables()` is not always sufficient for the Tooling API to select the correct JDK for the daemon. Using `setJavaHome()` is the authoritative way to ensure
  the specific JDK is used.
- **Alternatives considered**: Relying solely on the environment variable. Rejected due to the Tooling API's behavior of often using the current JVM or internal logic if `setJavaHome()` isn't called.

### 3. Path Validation

The system will validate that the resolved path (from environment or explicit arg) exists and is a directory.

- **Rationale**: Providing an invalid path to `setJavaHome()` can lead to cryptic build failures. Validating early allows for a clear warning log.

## Risks / Trade-offs

- **[Risk]** The environment's `JAVA_HOME` might point to a JDK version incompatible with the Gradle version being used.
    - **[Mitigation]** This is already a risk if a user provides an explicit `javaHome`. Gradle's Tooling API provides reasonably clear error messages in this case.
- **[Risk]** Users who relied on the Tooling API's default behavior (e.g., using the JVM that started the MCP server) might be surprised if their shell's `JAVA_HOME` is different.
    - **[Mitigation]** In most CLI contexts, the shell's `JAVA_HOME` is the intended JDK. If a user needs the default behavior, they can unset `JAVA_HOME` in their environment or provide an explicit override.
