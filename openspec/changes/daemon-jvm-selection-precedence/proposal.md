## Why

The build launcher currently promotes environment `JAVA_HOME` through `Launcher.setJavaHome` whenever no explicit `javaHome` is supplied. Because that Tooling API setting overrides Gradle's project daemon JVM configuration, builds can run on the wrong JVM even when the project declares daemon toolchain criteria or `org.gradle.java.home` (issue #238).

## What Changes

- Resolve daemon JVM selection with this terminal precedence: explicit `GradleInvocationArguments.javaHome`, project daemon JVM settings, environment `JAVA_HOME`, then the Tooling API default.
- Detect project daemon JVM settings before promoting environment `JAVA_HOME`, including effective `toolchainVersion` criteria and `org.gradle.java.home` in the effective Gradle user home or project root.
- Resolve the effective Gradle user home through the specified invocation and server channels, with first-non-blank semantics and case-insensitive environment-key matching on Windows.
- Treat an invalid explicit `javaHome` as terminal, warn, and omit `setJavaHome` rather than falling through to a lower-priority source.
- Enforce a valid explicit `javaHome` as an `org.gradle.java.home` build argument in addition to `Launcher.setJavaHome`, so it wins over lower-priority `gradle.properties` values — including invalid ones Gradle validates before applying the Tooling API java-home request.
- Add deterministic source-selection tests, isolated nested-build parity tests, implementation-time probes, updated tool metadata, and generated tool documentation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `build-execution`: Replace the incomplete Java-home launcher requirement with the normative daemon JVM selection precedence, effective-user-home resolution, validation, diagnostics, and fail-closed settings behavior.

## Impact

- **Build execution**: `BuildExecutionService`, a new daemon JVM selection helper, dependency injection, and launcher configuration behavior.
- **Invocation contract**: `GradleInvocationArguments.javaHome` metadata documents its terminal precedence and fallback behavior; the data shape does not change.
- **Test isolation**: Gradle connection/provider test plumbing, controlled Gradle user homes, shared distribution caching, and focused unit and nested-build tests.
- **Generated documentation**: `docs/tools/*.md` is regenerated after the metadata change.
- **Compatibility**: Existing public invocation fields remain unchanged; constructor extensions used for injection and test control are defaulted.
