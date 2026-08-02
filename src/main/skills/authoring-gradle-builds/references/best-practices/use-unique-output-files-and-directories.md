# Use unique output files and directories
Overlapping output files or directories cause tasks to rerun unnecessarily and waste work.  

## Explanation
Gradle tracks all output files and directories declared by tasks to decide whether a task needs to be rerun. For example, if the contents of a task's output directory change after its last execution, Gradle will rerun that task.  
Ensuring that each task uses its own unique output files and directories, both within a project and across the entire build, prevents unnecessary work.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outFileName = type.get() + ".txt"
        val message = "Hello " + type.get()
        outputDirectory.file(outFileName).get().asFile.writeText(message) (1)
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputDirectory.get().file("a.txt").asFile.readText() (2)
        outputFile.get().asFile.writeText(message)
    }
}

val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings") (3)
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings") (4)
}

tasks.register<ConsumerTask>("consumer") {
    inputDirectory = greeterA.flatMap { it.outputDirectory } (5)
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
```

build.gradle  

```groovy
abstract class GreetingTask extends DefaultTask {
    @Input
    abstract Property<String> getType()
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void run() {
        def outFileName = type.get() + ".txt"
        def message = "Hello " + type.get()
        outputDirectory.file(outFileName).get().asFile.text = message (1)
    }
}

abstract class ConsumerTask extends DefaultTask {
    @InputDirectory
    abstract DirectoryProperty getInputDirectory()
    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void run() {
        def message = inputDirectory.get().file("a.txt").asFile.text (2)
        outputFile.get().asFile.write(message)
    }
}

def greeterA = tasks.register("greeterA", GreetingTask) {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings") (3)
}
tasks.register("greeterB", GreetingTask) {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings") (4)
}

tasks.register("consumer", ConsumerTask) {
    inputDirectory = greeterA.flatMap { it.outputDirectory } (5)
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
```

|-------|-------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Write to a file in the output directory** : This task produces a single file in the `outputDirectory`, named based on the `type` input. |
| **2** | **Read a specific file in the input directory** : This task only needs to read a single `a.txt` file in the input directory.              |
| **3** | **Set output directory** : Sets `outputDirectory` to a subdirectory in `buildDirectory`.                                                  |
| **4** | **Set output directory** : Same as above, using the same shared `greetings` directory.                                                    |
| **5** | **Wire `greeterA` to consumer** : Makes sure that `greeterA` runs and produces the output directory before it is used by `consumer`.      |

With this setup, if you run the `consumer` task, then `greeterB`, the `consumer` task will be invalidated.  
The next time `consumer` is run it will **not** be `UP-TO-DATE` and will have to run again despite not using the output from `greeterB`.  
This happens because `greeterB` changes the contents of the shared output directory `greetings`, which is an output of `greeterA` that `consumer` depends on (despite `consumer` only actually using the unchanged `a.txt` file in that directory).  

### Do This Instead
To avoid issues, avoid using shared task output directories and files.  
Instead, tasks should only declare the exact outputs and consume the exact inputs that they actually produce and consume.  
The *simplest* change to make here is to use distinct output directories for each `GreetingTask`. This alone is sufficient to fix the problem.  
build.gradle.kts  

```kotlin
val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings")
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings-2") (1)
}
```

build.gradle  

```groovy
def greeterA = tasks.register("greeterA", GreetingTask) {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings")
}
tasks.register("greeterB", GreetingTask) {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings-2") (1)
}
```

|-------|--------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Set unique output directories** : Each `GreetingTask` is assigned its own unique output directory based on the `type` input. |

Now when running `consumer` task, then `greeterB`, then `consumer` task remains `UP-TO-DATE` as Gradle knows that it is not using the output from `greeterB`, since `greeterA` and `greeterB` write to distinct output directories.  
However, a more *complete and idiomatic* approach realizes that:  
1. Tasks that produce single output files should make this clear from the type of their `@Output` properties.

2. Tasks that only consume single input files should make this clear from the type of their `@Input` properties.

build.gradle.kts  

```kotlin
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputFile
    abstract val outputFile: RegularFileProperty (1)

    @TaskAction
    fun run() {
        val message = "Hello " + type.get()
        outputFile.get().asFile.writeText(message)
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty (2)
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputFile.get().asFile.readText()
        outputFile.get().asFile.writeText(message)
    }
}

val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("a.txt") } (3)
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("b.txt") }
}

tasks.register<ConsumerTask>("consumer") {
    inputFile = greeterA.map { it.outputFile.get() } (4)
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
```

build.gradle  

```groovy
abstract class GreetingTask extends DefaultTask {
    @Input
    abstract Property<String> getType()
    @OutputFile
    abstract RegularFileProperty getOutputFile() (1)

    @TaskAction
    void run() {
        def message = "Hello " + type.get()
        outputFile.get().asFile.text = message
    }
}

abstract class ConsumerTask extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getInputFile() (2)
    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void run() {
        def message = inputFile.get().asFile.text
        outputFile.get().asFile.write(message)
    }
}

def greeterA = tasks.register("greeterA", GreetingTask) {
    type = "a"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("a.txt") } (3)
}
tasks.register("greeterB", GreetingTask) {
    type = "b"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("b.txt") }
}

tasks.register("consumer", ConsumerTask) {
    inputFile = greeterA.map { it.outputFile.get() } (4)
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
```

|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Write to a specific output file** : This task produces a single file to a directly specified `outputFile` without registering an entire output directory.      |
| **2** | **Read a specific file** : Unlike the previous example the input is a single directly specified `inputFile` file.                                                |
| **3** | **Set output file** : Sets `outputFile` to a file that is inside a `shared` subdirectory of `buildDirectory`.                                                    |
| **4** | **Wire `greeterA` to consumer** : Makes sure that `greeterA` produces the output file before it is used by `consumer` by wiring task inputs to outputs directly. |

Now when running `consumer` task, then `greeterB`, the `consumer` task remains `UP-TO-DATE` as Gradle knows that it is not using the output from `greeterB`, since `greeterA` and `greeterB` *produce and consume distinct files* (that happen to be in created in the same directory).

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
