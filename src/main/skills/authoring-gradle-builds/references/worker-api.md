# Worker API

Use the Worker API to fan out independent, bounded units of task work. A worker action must be self-contained: pass declared, serializable parameters and do not retain `Project`, `Task`, mutable extensions, or other live Gradle model state. Use the Worker API only when parallel work or isolation solves a measured problem; do not replace a normal task action with workers for a small sequential operation.

## Non-negotiable defaults

- Inject `WorkerExecutor`; never construct it or obtain it from an internal Gradle service.
- Prefer `noIsolation()` for stateless, thread-safe work that can safely share the task's process and classpath.
- Select `classLoaderIsolation { ... }` only when the action needs a separated classpath or must avoid dependency/classloader conflicts.
- Select `processIsolation { ... }` only when the action needs JVM-level memory or JVM-argument isolation. Pay the process and heap cost deliberately.
- Define a `WorkAction<WorkParameters>` with managed parameter properties. Pass values through `parameters`, not through captured variables or global state.
- Submit all work from the task action. Gradle waits for submitted work before the task completes, so do not add an ad hoc join, executor, or background thread.

For constructor and service-injection rules, see [Advanced Configuration](advanced-configuration.md). The Worker API is not a replacement for a build service: use a build service for build-scoped shared resources, and use `maxParallelUsages` to bound them.

## Minimal worker task

```kotlin
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

abstract class ProcessFilesTask @Inject constructor(
    private val workers: WorkerExecutor,
) : DefaultTask() {
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @TaskAction
    fun process() {
        val queue = workers.noIsolation()
        inputFiles.files.forEach { file ->
            queue.submit(ProcessFileWork::class.java) {
                inputFile.set(file)
            }
        }
    }
}

interface ProcessFileParameters : WorkParameters {
    val inputFile: Property<java.io.File>
}

abstract class ProcessFileWork : WorkAction<ProcessFileParameters> {
    override fun execute() {
        val file = parameters.inputFile.get()
        // Perform independent, thread-safe work for this file.
        println("Processing ${file.name}")
    }
}
```

Keep each parameter complete and immutable after submission. If work writes files, give every action a unique output path and declare the aggregate outputs on the task. Do not have workers write the same file, append to one shared stream, or mutate shared collections without an explicit thread-safe design.

## Isolation modes and cost

| Mode | Execution boundary | Use when | Memory and cost |
| --- | --- | --- | --- |
| `noIsolation()` | Same Gradle process and shared classloader context | Work is trusted, lightweight, stateless, and classpath-compatible | Lowest overhead and memory use; a leak or unsafe global can damage the daemon or race with other workers |
| `classLoaderIsolation { classpath.from(...) }` | Separate classloader for the work action | Dependencies must be isolated from Gradle or from another worker classpath | More class metadata and classloader memory; still shares the Gradle JVM and its process limits |
| `processIsolation { forkOptions { ... } }` | Separate JVM process | Work needs JVM arguments, a separate heap, or stronger failure/memory containment | Highest startup and memory cost; each active process has its own JVM overhead and heap |

Example process isolation is appropriate for genuinely memory-heavy work, not for every item in a small loop:

```kotlin
abstract class AnalyzeFilesTask @Inject constructor(
    private val workers: WorkerExecutor,
) : DefaultTask() {
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @TaskAction
    fun analyze() {
        val queue = workers.processIsolation {
            forkOptions {
                maxHeapSize = "512m"
                jvmArgs("-Danalysis.mode=ci")
            }
        }
        inputFiles.files.forEach { file ->
            queue.submit(AnalyzeFileWork::class.java) {
                inputFile.set(file)
            }
        }
    }
}

interface AnalyzeFileParameters : WorkParameters {
    val inputFile: Property<java.io.File>
}

abstract class AnalyzeFileWork : WorkAction<AnalyzeFileParameters> {
    override fun execute() {
        analyze(parameters.inputFile.get())
    }

    private fun analyze(file: java.io.File) {
        println("Analyzing ${file.name}")
    }
}
```

For classloader isolation, provide only the action's required runtime classpath and keep the action implementation compatible with that classpath. Do not assume project-scoped services are available inside an isolated action. In particular, classloader- and process-isolated workers cannot directly consume a build service; pass serializable parameters or use `noIsolation()` when safe.

## When not to use the Worker API

Do not use workers when the operation is sequential, tiny, already parallelized by a library, or dominated by one shared lock or one output file. Do not use process isolation to conceal undeclared inputs, fix a race, or compensate for an unbounded workload. Fix task input/output modeling and synchronization at the root.

**Anti-patterns**:

- Capturing `project`, `layout`, an extension, or a mutable collection in a `WorkAction`.
- Passing a non-serializable service, open stream, socket, or mutable model object as a worker parameter.
- Calling `System.getenv`, reading undeclared files, or resolving configurations from worker code without modeling the value as task/work input.
- Starting a second executor and returning before submitted work finishes.
- Submitting one process-isolated worker per trivial operation and exhausting memory.
- Assuming worker submission order is execution order.

## Version notes

- **Gradle 9.x:** Bias to the latest 9.x API and current worker diagnostics. Use process isolation only with an explicit memory budget; configuration-cache compatibility still depends on the surrounding task and plugin.
- **Gradle 8.x:** `WorkerExecutor`, `WorkAction`, `WorkParameters`, and the three isolation modes are supported. Test the exact worker classpath and plugin combination when adopting configuration cache.
- **Gradle 7.x:** The Worker API is available. Prefer public API signatures documented by the target 7.x wrapper, use managed parameter types, and fall back to `noIsolation()` when newer classpath or fork-option details are unavailable. Do not assume Gradle 7.x configuration-cache compatibility.

**More info**:

- Worker API: `gradle_docs` `tag:userguide`, path `userguide/worker_api.md`, terms `noIsolation`, `classLoaderIsolation`, `processIsolation`
- Service injection: `gradle_docs` `tag:userguide`, path `userguide/service_injection.md`, terms `@Inject`, `WorkerExecutor`
- Shared build services: `gradle_docs` `tag:userguide`, path `userguide/build_services.md`, terms `usesService`, `maxParallelUsages`
- Version-matched research: `gradle_docs`
