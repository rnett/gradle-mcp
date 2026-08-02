# Do not call `get()` on a Provider outside a Task action
When configuring tasks and extensions do not call [`get()` (Use `gradle_docs(path="javadoc/org/gradle/api/provider/Provider.md")`.)) on a provider, use [`map()` (Use `gradle_docs(path="javadoc/org/gradle/api/provider/Provider.md")`.)), or [`flatMap()` (Use `gradle_docs(path="javadoc/org/gradle/api/provider/Provider.md")`.)) instead.  

## Explanation
A provider should be evaluated as late as possible. Calling `get()` forces immediate evaluation, which can trigger unintended side effects, such as:  
* The value of the provider becomes an input to configuration, causing potential configuration cache misses.

* The value may be evaluated too early, meaning you might not be using the final or correct value of the property. This may lead to painful and hard to debug ordering issues.

* It breaks Gradle's ability to build dependencies and to track task inputs and outputs, making automatic task dependency wiring impossible. See [Working with task inputs and outputs (Use `gradle_docs(path="userguide/lazy_configuration.md")`.)

It is preferable to avoid explicitly evaluating a `Provider` at all, and deferring to `map`/`flatMap` to connect `Providers` to `Providers` implicitly.  

## Example
Here is a task that writes an input `String` to a file:  
build.gradle.kts  

```kotlin
abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val myInput: Property<String>

    @get:OutputFile
    abstract val myOutput: RegularFileProperty

    @TaskAction
    fun doAction() {
        val outputFile = myOutput.get().asFile
        val outputText = myInput.get() (1)
        println(outputText)
        outputFile.writeText(outputText)
    }
}

val currentEnvironment: Provider<String> = providers.gradleProperty("currentEnvironment").orElse("234") (2)
```

build.gradle  

```groovy
abstract class MyTask extends DefaultTask {
    @Input
    abstract Property<String> getMyInput()

    @OutputFile
    abstract RegularFileProperty getMyOutput()

    @TaskAction
    void doAction() {
        def outputFile = myOutput.get().asFile
        def outputText = myInput.get() (1)
        println(outputText)
        outputFile.write(outputText)
    }
}

Provider<String> currentEnvironment = providers.gradleProperty("currentEnvironment").orElse("234") (2)
```

|-------|----------------------------------------------|
| **1** | Using `Provider.get()` in the task action    |
| **2** | Gradle property that we wish to use as input |

### Don't Do This
You could call `get()` at configuration time to set up this task:  
build.gradle.kts  

```kotlin
tasks.register<MyTask>("avoidThis") {
    myInput = "currentEnvironment=${currentEnvironment.get()}"  (1)
    myOutput = layout.buildDirectory.get().asFile.resolve("output-avoid.txt")  (2)
}
```

build.gradle  

```groovy
tasks.register("avoidThis", MyTask) {
    myInput = "currentEnvironment=${currentEnvironment.get()}"  (1)
    myOutput = new File(layout.buildDirectory.get().asFile, "output-avoid.txt")  (2)
}
```

|-------|------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Reading the value of `currentEnvironment` at configuration time**: This value might change by the time the task start executing. |
| **2** | **Reading the value of `buildDirectory` at configuration time**: This value might change by the time the task start executing.     |

### Do This Instead
Instead, you should explicitly wire task inputs and outputs like this:  
build.gradle.kts  

```kotlin
tasks.register<MyTask>("doThis") {
    myInput = currentEnvironment.map { "currentEnvironment=$it" }  (1)
    myOutput = layout.buildDirectory.file("output-do.txt")  (2)
}
```

build.gradle  

```groovy
tasks.register("doThis", MyTask) {
    myInput = currentEnvironment.map { "currentEnvironment=$it" }  (1)
    myOutput = layout.buildDirectory.file("output-do.txt")  (2)
}
```

|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Using `map()` to transform `currentEnvironment`** : `map` transform runs only when the value is read.                                                |
| **2** | **Using `file()` to create a new `Provider<RegularFile>`** : the value of the `buildDirectory` is only checked when the value of the provider is read. |

## References
* [Task Inputs and Outputs (Use `gradle_docs(path="userguide/incremental_build.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
