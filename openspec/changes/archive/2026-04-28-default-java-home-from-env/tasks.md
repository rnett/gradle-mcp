## 1. Preparation

- [x] 1.1 Update `GradleInvocationArguments` documentation for `javaHome` in `GradleArgs.kt` to reflect the new default behavior.

## 2. Implementation

- [x] 2.1 Update `BuildExecutionService.configureLauncher` to resolve `javaHome` with environment fallback from `actualEnvVars`.
- [x] 2.2 Ensure the resolved Java home is validated (exists and is a directory) before calling `launcher.setJavaHome()`.

## 3. Verification

- [x] 3.1 Update `GradleProviderTest` to include a test case verifying `JAVA_HOME` fallback from the environment.
- [x] 3.2 Add a test case verifying that an explicit `javaHome` argument takes precedence over the environment's `JAVA_HOME`.
- [x] 3.3 Verify that the system still falls back to the Tooling API default when no Java home is specified or found in the environment.

## 4. Metadata Update

- [x] 4.1 Run `./gradlew :updateToolsList` to synchronize auto-generated documentation and LLM metadata.
