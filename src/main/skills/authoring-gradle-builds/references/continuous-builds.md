<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Continuous Builds

Continuous builds allow Gradle to monitor your project for changes and automatically re-execute tasks when relevant files are modified.

## Enabling Continuous Mode

Use the `--continuous` (or `-t`) flag when running a task:

```bash
./gradlew test --continuous
```

Gradle will execute the `test` task and then remain active, waiting for file changes to trigger a re-run.

## File System Watching

### The `--watch-fs` Flag

Recent Gradle versions introduce `--watch-fs`, which optimizes how the build system detects changes by using native OS file system events instead of polling.

```bash
./gradlew assemble --watch-fs
```

`--watch-fs` is generally more performant and reduces CPU overhead during long-running watch sessions.

## Use Cases

### Iterative Development

The primary use case is a tight feedback loop. For example, running tests in continuous mode allows you to see a regression instantly after hitting "Save" in your IDE.

### Auto-Generated Sources

If your build generates code (e.g., via Protobuf or OpenAPI), continuous mode ensures that the downstream compilation and test tasks trigger automatically once the generation task completes.

## Limitations and Caveats

### Manual Restarts

Continuous builds do not always detect new files created in the project directory. If you add a new class or resource file, you may need to restart the Gradle process for the change to be recognized.

### Configuration Cache Interactions

The Configuration Cache stores the result of the configuration phase. When running in continuous mode:
- Gradle will reuse the cached configuration if inputs haven't changed.
- Changes to `build.gradle.kts` or `settings.gradle.kts` will trigger a re-configuration before the task re-runs.

### Resource Consumption

Keeping a build active in `--continuous` mode consumes a JVM slot and memory. Ensure you terminate these processes when switching branches or stopping work for the day.
