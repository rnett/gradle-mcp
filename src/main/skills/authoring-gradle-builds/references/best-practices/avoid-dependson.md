<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: d7a4208f2eea132235fcaaf730ff9e83d8cfa674357e4e1d413a77fcab25748b
-->
# Avoid DependsOn
The task [dependsOn](https://docs.gradle.org/current/javadoc/org/gradle/api/DefaultTask.html#setDependsOn(java.lang.Iterable) (Use `gradle_docs(path="javadoc/org/gradle/api/DefaultTask.html#setDependsOn(java.lang.Iterable")`.)) method should only be used for [lifecycle tasks](https://docs.gradle.org/current/userguide/organizing_tasks.html#sec:lifecycle_tasks) (Use `gradle_docs(path="userguide/organizing_tasks.html#sec:lifecycle_tasks")`.) (tasks without task actions).  

## Explanation
Tasks with actions should declare their inputs and outputs so that Gradle's up-to-date checking can automatically determine when these tasks need to be run or rerun.  
Using `dependsOn` to link tasks is a much coarser-grained mechanism that does **not** allow Gradle to understand why a task requires a prerequisite task to run, or which specific files from a prerequisite task are needed. `dependsOn` forces Gradle to assume that *every* file produced by a prerequisite task is needed by this task. This can lead to unnecessary task execution and decreased build performance.  

## Example
Here is a task that writes output to two separate files:  
build.gradle.kts  

```kotlin
abstract class SimplePrintingTask : DefaultTask() {
    @get:OutputFile
    abstract val messageFile: RegularFileProperty

    @get:OutputFile
    abstract val audienceFile: RegularFileProperty

    @TaskAction (1)
    fun run() {
        messageFile.get().asFile.writeText("Hello")
        audienceFile.get().asFile.writeText("World")
    }
}

tasks.register<SimplePrintingTask>("helloWorld") { (2)
    messageFile.set(layout.buildDirectory.file("message.txt"))
    audienceFile.set(layout.buildDirectory.file("audience.txt"))
}
```

build.gradle  

```groovy
abstract class SimplePrintingTask extends DefaultTask {
    @OutputFile
    abstract RegularFileProperty getMessageFile()

    @OutputFile
    abstract RegularFileProperty getAudienceFile()

    @TaskAction (1)
    void run() {
        messageFile.get().asFile.write("Hello")
        audienceFile.get().asFile.write("World")
    }
}

tasks.register("helloWorld", SimplePrintingTask) { (2)
    messageFile = layout.buildDirectory.file("message.txt")
    audienceFile = layout.buildDirectory.file("audience.txt")
}
```

|-------|---------------------------------------------------------------------------------------------------------------------------|
| **1** | **Task With Multiple Outputs** : `helloWorld` task prints "Hello" to its `messageFile` and "World" to its `audienceFile`. |
| **2** | **Registering the Task** : `helloWorld` produces "message.txt" and "audience.txt" outputs.                                |

### Don't Do This
If you want to translate the greeting in the `message.txt` file using another task, you could do this:  
build.gradle.kts  

```kotlin
abstract class SimpleTranslationTask : DefaultTask() {
    @get:InputFile
    abstract val messageFile: RegularFileProperty

    @get:OutputFile
    abstract val translatedFile: RegularFileProperty

    init {
        messageFile.convention(project.layout.buildDirectory.file("message.txt"))
        translatedFile.convention(project.layout.buildDirectory.file("translated.txt"))
    }

    @TaskAction (1)
    fun run() {
        val message = messageFile.get().asFile.readText(Charsets.UTF_8)
        val translatedMessage = if (message == "Hello") "Bonjour" else "Unknown"

        logger.lifecycle("Translation: " + translatedMessage)
        translatedFile.get().asFile.writeText(translatedMessage)
    }
}

tasks.register<SimpleTranslationTask>("translateBad") {
    dependsOn(tasks.named("helloWorld")) (2)
}
```

build.gradle  

```groovy
abstract class SimpleTranslationTask extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getMessageFile()

    @OutputFile
    abstract RegularFileProperty getTranslatedFile()

    SimpleTranslationTask() {
        messageFile.convention(project.layout.buildDirectory.file("message.txt"))
        translatedFile.convention(project.layout.buildDirectory.file("translated.txt"))
    }

    @TaskAction (1)
    void run() {
        def message = messageFile.get().asFile.text
        def translatedMessage = message == "Hello" ? "Bonjour" : "Unknown"

        logger.lifecycle("Translation: " + translatedMessage)
        translatedFile.get().asFile.write(translatedMessage)
    }
}

tasks.register("translateBad", SimpleTranslationTask) {
    dependsOn(tasks.named("helloWorld")) (2)
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Translation Task Setup** : `translateBad` requires `helloWorld` to run first to produce the message file otherwise it will fail with an error as the file does not exist. |
| **2** | **Explicit Task Dependency** : Running `translateBad` will cause `helloWorld` to run first, but Gradle does not understand *why*.                                           |

### Do This Instead
Instead, you should explicitly wire task inputs and outputs like this:  
build.gradle.kts  

```kotlin
abstract class SimpleTranslationTask : DefaultTask() {
    @get:InputFile
    abstract val messageFile: RegularFileProperty

    @get:OutputFile
    abstract val translatedFile: RegularFileProperty

    init {
        messageFile.convention(project.layout.buildDirectory.file("message.txt"))
        translatedFile.convention(project.layout.buildDirectory.file("translated.txt"))
    }

    @TaskAction (1)
    fun run() {
        val message = messageFile.get().asFile.readText(Charsets.UTF_8)
        val translatedMessage = if (message == "Hello") "Bonjour" else "Unknown"

        logger.lifecycle("Translation: " + translatedMessage)
        translatedFile.get().asFile.writeText(translatedMessage)
    }
}

tasks.register<SimpleTranslationTask>("translateGood") {
    inputs.file(tasks.named<SimplePrintingTask>("helloWorld").map { messageFile }) (1)
}
```

build.gradle  

```groovy
abstract class SimpleTranslationTask extends DefaultTask {
    @InputFile
    abstract RegularFileProperty getMessageFile()

    @OutputFile
    abstract RegularFileProperty getTranslatedFile()

    SimpleTranslationTask() {
        messageFile.convention(project.layout.buildDirectory.file("message.txt"))
        translatedFile.convention(project.layout.buildDirectory.file("translated.txt"))
    }

    @TaskAction (1)
    void run() {
        def message = messageFile.get().asFile.text
        def translatedMessage = message == "Hello" ? "Bonjour" : "Unknown"

        logger.lifecycle("Translation: " + translatedMessage)
        translatedFile.get().asFile.write(translatedMessage)
    }
}

tasks.register("translateGood", SimpleTranslationTask) {
    inputs.file(tasks.named("helloWorld", SimplePrintingTask).map { messageFile }) (1)
}
```

|-------|--------------------------------------------------------------------------------------------------------------------------|
| **1** | **Register Implicit Task Dependency** : `translateGood` requires only one of the files that is produced by `helloWorld`. |

Gradle now understands that `translateGood` requires `helloWorld` to have run successfully first because it needs to create the `message.txt` file which is then used by the translation task. Gradle can use this information to optimize task scheduling. Using the `map` method avoids eagerly retrieving the `helloWorld` task until the output is needed to determine if `translateGood` should run.  

## References
* [Task Inputs and Outputs](https://docs.gradle.org/current/userguide/incremental_build.html#sec:task_input_output_side_effects) (Use `gradle_docs(path="userguide/incremental_build.html#sec:task_input_output_side_effects")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
