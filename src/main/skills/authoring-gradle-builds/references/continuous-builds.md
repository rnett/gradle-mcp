<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Continuous Builds

Author a build so `--continuous` can re-run a task when a declared input changes. Continuous execution is a Gradle runtime feature, not a second incremental-build model: the watcher observes the task graph's declared inputs and Gradle decides whether the task is out-of-date, up-to-date, or from-cache.

Running a continuous build belongs to the `using-gradle` skill. This reference defines what build authors must declare so a continuous invocation behaves predictably; it does not prescribe a long-lived command or process-management workflow.

## Non-negotiable defaults

- Declare every source file, generated input, configuration value, and relevant directory with task input annotations or managed properties.
- Declare every output and give each task ownership of its output paths. A change outside the declared input set is not a reliable trigger for the task.
- Prefer lazy task registration and providers. Do not read files, environment variables, system properties, or external command output during configuration unless the value is modeled and intentionally configuration-time.
- Treat continuous mode as a bounded interactive session. Do not run it in CI, as a permanent background service, or while switching branches without stopping it.
- Stop the process explicitly when the session ends. A continuous build keeps a JVM, Gradle daemon, and file-watching resources active.

## What triggers a rebuild

Gradle re-executes when a watched, declared input changes and the selected task graph is no longer up-to-date. Typical inputs include:

- `@Input`, `@InputFile`, `@InputFiles`, `@InputDirectory`, and managed `Property` values.
- Source directories and generated files connected to the task through `ConfigurableFileCollection`, `FileCollection`, or providers.
- Build-script, settings, plugin, and dependency changes when they invalidate configuration or the task graph.

Do not promise that "any file in the repository" triggers a rebuild. Undeclared files, ignored files, external services, current time, and hidden environment reads are not dependable task inputs. A new source file is detected only when it is under a declared, watched input directory and the task's file sensitivity is modeled correctly. If the build changes its task graph or input model, restart the continuous session when the existing process cannot observe that structural change.

Example of a task with explicit inputs and outputs:

```kotlin
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateIndexTask : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile
    abstract val indexFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val lines = sources.files
            .sortedBy { it.path }
            .flatMap { it.readLines() }
        indexFile.get().asFile.writeText(lines.joinToString("\n"))
    }
}

tasks.register<GenerateIndexTask>("generateIndex") {
    sources.from(layout.projectDirectory.dir("src/main/resources"))
    indexFile.set(layout.buildDirectory.file("generated/index.txt"))
}
```

Use the established best-practices corpus for file-input sensitivity and unique output paths instead of restating those policies here: [Use path sensitivity for file inputs](best-practices/use-pathsensitivity-none-for-file-inputs-and-pathsensitivity-relative-for-directories.md) and [Use unique output files and directories](best-practices/use-unique-output-files-and-directories.md).

## Resource consumption and lifecycle

A continuous build is long-lived. It consumes a JVM slot, Gradle daemon memory, file descriptors or native watcher handles, and CPU while processing changes. Repeated rebuilds also consume compiler, test, worker, and build-cache resources. Keep the task graph narrow, avoid unnecessary broad directory inputs, and stop the process before changing branches or deleting generated directories.

**Anti-patterns**:

- Watching the repository root when the task needs one source directory.
- Starting a continuous build in CI or leaving it detached after an interactive session.
- Treating file watching as a substitute for declaring task inputs.
- Having multiple continuous invocations own the same generated output directory.
- Starting custom polling threads or file watchers from build logic.

## Configuration cache interaction

Configuration cache and continuous execution solve different phases. The configuration cache can reuse the configured task graph between iterations when its declared configuration inputs are unchanged. A change to build logic, settings, plugin inputs, or another configuration-cache input can invalidate the entry and force reconfiguration before the task runs again.

Keep configuration-cache safety at the source: use managed providers and `ValueSource` for deliberate environment, system-property, file, and external-command reads; do not hide those reads in task actions or worker closures. See [Advanced Configuration](advanced-configuration.md) for the full value-source and provider rules. Do not assume `--continuous` makes undeclared external state observable, and do not assume a reused configuration cache notices a hidden environment change.

## Version notes

- **Gradle 9.x:** Bias to the latest 9.x continuous-build behavior and diagnostics. Validate watcher behavior on the target operating system and keep the watched input set narrow.
- **Gradle 8.x:** Continuous builds and configuration cache are supported; configuration cache is stable from 8.1, but plugin/task compatibility remains build-specific.
- **Gradle 7.x:** Continuous builds are available, but file-system watcher behavior and configuration-cache support require explicit wrapper and operating-system testing. Fall back to ordinary incremental task inputs and restart the session when configuration changes are not observed reliably.

**More info**:

- Continuous execution: `gradle_docs` `tag:userguide`, path `userguide/continuous_builds.md`
- Incremental task inputs and outputs: `gradle_docs` `tag:userguide`, path `userguide/more_about_tasks.md`
- Configuration-cache requirements: `gradle_docs` `tag:userguide`, path `userguide/configuration_cache_requirements.md`
- Running and stopping long-lived builds: `gradle` / `wait_build` / `query_build`
