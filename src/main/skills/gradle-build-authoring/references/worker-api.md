# Worker API

The Gradle Worker API allows you to offload units of work to separate processes or threads. This is essential for tasks that need to run in parallel, tasks that require a different JVM version, or tasks that would otherwise cause memory leaks in the main Gradle process.

## Basic Usage with `WorkerExecutor`

To use the Worker API, inject the `WorkerExecutor` into your custom task.

### Simple Work Submission
```kotlin
abstract class MyParallelTask : DefaultTask() {
    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun run() {
        val workQueue = workerExecutor.noIsolation()
        
        (1..5).forEach { id ->
            workQueue.submit(MyWorkAction::class.java) {
                this.id = id
            }
        }
    }
}

interface MyWorkParameters : WorkParameters {
    var id: Int
}

abstract class MyWorkAction : WorkAction<MyWorkParameters> {
    override fun execute() {
        println("Processing work item ${parameters.id}")
    }
}
```

## Isolation Modes

The Worker API provides three isolation levels to protect the build process and manage resources.

### 1. No Isolation (`noIsolation`)
- **Behavior**: Work runs in the same process as the Gradle daemon.
- **Use Case**: Lightweight tasks that don't need a separate classpath or JVM.
- **Performance**: Fastest, as there is no process overhead.

### 2. ClassLoader Isolation (`classLoaderIsolation`)
- **Behavior**: Each worker gets its own classloader.
- **Use Case**: Use this when the work requires a set of dependencies that might conflict with the Gradle daemon's classpath.
- **Configuration**: Provide a classpath via the `classLoaderIsolation` configuration block.

### 3. Process Isolation (`processIsolation`)
- **Behavior**: Work runs in a completely separate JVM process.
- **Use Case**: Use this for memory-intensive tasks (to avoid OOM in the daemon) or tasks that need specific JVM arguments (e.g., `-Xmx4g`).
- **Configuration**: Allows specifying JVM arguments and a separate heap size.

## Parallel Work Patterns

The Worker API allows for "fan-out" parallelism. Instead of a single `@TaskAction` doing a loop, you submit many `WorkAction` items to the queue.

### Example: Parallel File Processing
```kotlin
abstract class FileProcessorTask : DefaultTask() {
    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun process() {
        val workQueue = workerExecutor.processIsolation {
            // Configure separate JVM for isolation
            forkOptions {
                maxHeapSize = "512m"
            }
        }

        inputFiles.forEach { file ->
            workQueue.submit(FileWorkAction::class.java) {
                this.targetFile = file
            }
        }
    }
}

interface FileWorkParameters : WorkParameters {
    var targetFile: File
}

abstract class FileWorkAction : WorkAction<FileWorkParameters> {
    override fun execute() {
        val file = parameters.targetFile
        // Heavy processing logic here...
        println("Finished processing ${file.name}")
    }
}
```

## Key Considerations
- **State**: `WorkAction`s are short-lived and independent. Do not share mutable state between them.
- **Output**: Use the `WorkQueue` to submit work, and remember that the main task will wait for all submitted work to complete before continuing.
- **Memory**: Process isolation is the safest way to prevent a single problematic task from crashing the entire Gradle build.
