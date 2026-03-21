# Design: Explicit Java Home Support for Gradle Builds

## API Changes

### `GradleInvocationArguments`

- Add a nullable `javaHome: String?` property.
- Update `plus` operator to prioritize the `javaHome` from the right-hand side.
- Include `JAVA_HOME` in `renderCommandLine()` for debugging and logging transparency.

## Implementation Details

### `BuildExecutionService`

- In `configureLauncher`, check if `args.javaHome` is set.
- If set, validate that the path exists and is a directory.
- Call `launcher.setJavaHome(file)` to configure the Gradle Tooling API launcher.
- Log a warning if the specified path is invalid.

## Verification Plan

### Automated Tests

- `GradleProviderTest`: Add a test case `can run gradle build with explicit java home` that uses the current `java.home` to verify that setting the parameter does not break the build.

### Manual Verification

- Attempt to run a build with a deliberate invalid `javaHome` and verify the warning log.
- (If multiple JDKs available) Run a build with a different JDK and verify its usage via `gradle -v` in the console output.
