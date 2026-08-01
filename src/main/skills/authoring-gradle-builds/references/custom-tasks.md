<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Custom Tasks

Model task inputs and outputs, register tasks lazily, and keep task actions independent of the Gradle model. Use task dependencies to express lifecycle ordering, not to hide implementation coupling.

## Lazy Registration

Use `tasks.register` for new tasks and configure the returned `TaskProvider`. Use `tasks.named` to reference an existing task and `configureEach` for type-wide configuration.

```kotlin
abstract class MessageTask : DefaultTask() {
    @get:Input
    abstract val message: Property<String>

    @TaskAction
    fun run() {
        logger.lifecycle(message.get())
    }
}

tasks.register<MessageTask>("printMessage") {
    group = "verification"
    description = "Prints the configured message."
    message.set("Hello from a lazy task")
}
```

**Default:** Register tasks lazily, wire `Property` and `Provider` values without calling `get()` during configuration, and realize a task only when a lifecycle task or explicit task path requires it.

**Anti-pattern:** Use `tasks.create`, `tasks.getByName`, eager `withType<T>()`, or top-level `Provider.get()` for unrelated task configuration. Do not perform dependency resolution or expensive file/process work while registering a task.

See the frozen corpus entries [Avoid `afterEvaluate`](best-practices/avoid-afterevaluate.md) and [Do not call `get()` on a Provider outside a Task action](best-practices/do-not-call-get-on-a-provider-outside-a-task-action.md) for the approved lazy-model rationale.

## `group` and `description`

Set a stable `group` and actionable `description` on every custom task and ad hoc task. The task list is an agent-facing discovery surface.

```kotlin
tasks.register("generateMetadata") {
    group = "build setup"
    description = "Generates metadata consumed by packaging tasks."
}
```

**Default:** Use a short, consistent group and describe the observable effect, important inputs, and output role.

**Anti-pattern:** Leave custom tasks in the default group, use an empty description, or describe implementation details without stating what the task does.

See [Group and describe custom Tasks](best-practices/group-and-describe-custom-tasks.md).

## Inputs, Outputs, and Cacheability

Declare every value read by a task as an input and every generated file as an output. Use managed properties and injected Gradle services rather than retaining `Project` for execution-time access.

```kotlin
@CacheableTask
abstract class GenerateReportTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val format: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val source = sourceFile.get().asFile.readText()
        reportFile.get().asFile.writeText("${format.get()}: $source")
    }
}

tasks.register<GenerateReportTask>("generateReport") {
    group = "reporting"
    description = "Generates a report from the source file."
    sourceFile.set(layout.projectDirectory.file("input.txt"))
    format.set("plain")
    reportFile.set(layout.buildDirectory.file("reports/report.txt"))
}
```

Use `@CacheableTask` only when the task is deterministic for its declared inputs and produces fully modeled outputs. If a task cannot safely be cached, use `@DisableCachingByDefault` and document why. Prefer `@PathSensitive(PathSensitivity.NONE)` for path-insensitive file content and `RELATIVE` for directory layouts where relative paths matter.

**Default:** Model inputs and outputs explicitly, use unique output paths, and choose cacheability based on declared inputs and deterministic behavior. Inject `ExecOperations`, `FileSystemOperations`, `ArchiveOperations`, `ProjectLayout`, or `ProviderFactory` when task actions need Gradle services.

**Anti-pattern:** Mark a task cacheable while it reads undeclared environment state, wall-clock time, network data, or arbitrary files; write overlapping outputs; call `project.file("input.txt")`, `project.exec { commandLine("tool") }`, or `task.project` in `@TaskAction`; or resolve a configuration during configuration.

See [Favor `@CacheableTask` and `@DisableCachingByDefault`](best-practices/favor-cacheabletask-and-disablecachingbydefault-over-cacheif-spec-and-donotcacheif-string-spec.md), [Use unique output files and directories](best-practices/use-unique-output-files-and-directories.md), and [Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directories](best-practices/use-pathsensitivity-none-for-file-inputs-and-pathsensitivity-relative-for-directories.md).

## Task Configuration Avoidance

Keep configuration code side-effect free and use providers to connect model values. A task action is the execution boundary: realize values there, not while configuring unrelated tasks.

```kotlin
val generatedDir = layout.buildDirectory.dir("generated")

tasks.register<Sync>("prepareGenerated") {
    from(generatedDir)
    into(layout.buildDirectory.dir("prepared"))
}

tasks.withType<Delete>().configureEach {
    // Configure all matching tasks without realizing them eagerly.
}
```

**Default:** Prefer `map`, `flatMap`, `convention`, `set`, `from`, and `into` provider wiring. Keep task actions small and use only declared inputs and injected services.

**Anti-pattern:** Use `afterEvaluate` as a synchronization mechanism, call `Provider.get()` in plugin application code, or use a task action as a place to mutate another project's model.

## `dependsOn` and Lifecycle Tasks

Use `dependsOn` only on lifecycle tasks that have no task actions and aggregate work. For data flow, prefer modeled inputs and outputs so Gradle can infer relationships; use explicit task dependencies when a producer-consumer edge cannot be inferred.

```kotlin
tasks.register("checkAll") {
    group = "verification"
    description = "Runs all verification tasks."
    dependsOn(tasks.withType<Test>())
}
```

**Default:** Let lifecycle tasks aggregate leaf tasks. Prefer `from`, `builtBy`, artifact configurations, and task properties for producer-consumer relationships.

**Anti-pattern:** Add broad `dependsOn` chains between implementation tasks, make every task depend on every other task, or use `dependsOn` to compensate for missing input/output modeling.

## Execution Control and Ordering

Use task-dependency edges for execution order when producer-consumer data flow cannot be inferred. Use `mustRunAfter` and `shouldRunAfter` to constrain order without introducing a hard dependency.

```kotlin
val taskA = tasks.register("taskA") { /* ... */ }
val taskB = tasks.register("taskB") { /* ... */ }

// taskB will run after taskA if both are in the graph, but taskB does not force taskA to run.
taskB.configure {
    mustRunAfter(taskA)
}
```

**Default:** Use `mustRunAfter` for strict ordering of independent tasks. Use `shouldRunAfter` for preferred ordering that Gradle can ignore to avoid cycles. Use finalizer tasks (via `finalizedBy`) to ensure cleanup or reporting occurs even after a task fails.

**Anti-pattern:** Use `dependsOn` for a "run this first" constraint; this forces the dependency to run every time, which can break lazy registration and prolong build times. Do not define circular ordering constraints; Gradle will fail the build if a cycle is detected.

## Incremental Task Model

Enable incremental processing to avoid re-processing all inputs when only a subset has changed. Use `InputChanges` to identify added, modified, and removed files in a task action.

```kotlin
abstract class IncrementalTask : DefaultTask() {
    @get:Incremental
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        inputChanges.getFileChanges(inputFiles).forEach { change ->
            if (change.changeType == ChangeType.REMOVED) {
                // Delete corresponding output
            } else {
                // Process change.file
            }
        }
    }
}
```

**Default:** Use `@Incremental` on input collections and query `InputChanges` in the action to implement a "delta-only" execution path. Use `@SkipWhenEmpty` to avoid executing a task when its inputs are missing.

**Anti-pattern:** Implement manual file-timestamp checks or use `File.lastModified()` inside a task action; these are fragile, bypass the build cache, and are not compatible with remote caches.

## Build Cache Authoring

Ensure tasks are deterministic to maximize cache hits. A task is cacheable if its output is a pure function of its `@Input` and `@InputFile` values.

**Default:** Apply `@CacheableTask` only to deterministic tasks. Use path sensitivity (`@PathSensitivity`) to decide if the absolute path of an input should affect the cache key. Prefer `RELATIVE` for directories and `NONE` for content-only matching.

**Anti-pattern:** Use `@CacheableTask` on tasks that rely on system environment variables, wall-clock time, or undeclared project-state; this leads to "cache poisoning" where incorrect results are served to other developers.

See [Managed Types and Providers](managed-types-and-providers.md) for wiring inputs lazily to maintain cacheability.

### Version notes

- **Gradle 9.x:** Lazy registration, provider wiring, task input/output modeling, and explicit lifecycle-only `dependsOn` are current best-practice defaults. Configuration cache is stable but remains build/plugin-specific.
- **Gradle 8.x:** The same APIs apply; configuration cache is stable from 8.1, while 8.0 requires compatibility testing.
- **Gradle 7.x:** Use `tasks.register`, managed properties, and declared inputs/outputs. Configuration cache is an experimental fallback, so test compatibility explicitly rather than assuming it.

**More info:**

- Task registration: `gradle_docs` `tag:userguide`, path `userguide/task_configuration_avoidance.md`; published guide: https://docs.gradle.org/current/userguide/task_configuration_avoidance.html
- Task inputs and outputs: `gradle_docs` `tag:best-practices`, path `userguide/best_practices_tasks.md`, terms `inputs outputs cacheable task`; published guide: https://docs.gradle.org/current/userguide/best_practices_tasks.html
- Configuration-cache requirements: `gradle_docs` `tag:userguide`, path `userguide/configuration_cache_requirements.md`; published guide: https://docs.gradle.org/current/userguide/configuration_cache_requirements.html
- Task execution and inspection through MCP: https://gradle-mcp.rnett.dev/latest/tools/EXECUTION_TOOLS/
- Gradle documentation lookup: https://gradle-mcp.rnett.dev/latest/tools/GRADLE_DOCS_TOOLS/
