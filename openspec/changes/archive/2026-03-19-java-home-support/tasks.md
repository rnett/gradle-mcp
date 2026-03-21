# Tasks: Explicit Java Home Support for Gradle Builds

- [x] Add `javaHome` property to `GradleInvocationArguments`.
- [x] Implement `javaHome` configuration in `BuildExecutionService.configureLauncher`.
- [x] Update `renderCommandLine` to include `JAVA_HOME`.
- [x] Add verification test in `GradleProviderTest`.
- [x] Verify that builds still run correctly with and without the parameter.
