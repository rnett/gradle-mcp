<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: f480b700ba3c8ecea076c38f44b91b9ea6198cc493adf4dc4a669020cd2640c9
-->
# Favor `@CacheableTask` and `@DisableCachingByDefault` over `cacheIf(Spec)` and `doNotCacheIf(String, Spec)`
The [`cacheIf`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/TaskOutputs.html#cacheIf(org.gradle.api.specs.Spec) (Use `gradle_docs(path="javadoc/org/gradle/api/tasks/TaskOutputs.html#cacheIf(org.gradle.api.specs.Spec")`.)) and [`doNotCacheIf`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/TaskOutputs.html#doNotCacheIf(java.lang.String,org.gradle.api.specs.Spec) (Use `gradle_docs(path="javadoc/org/gradle/api/tasks/TaskOutputs.html#doNotCacheIf(java.lang.String,org.gradle.api.specs.Spec")`.)) methods should only be used in situations where the [cacheability](https://docs.gradle.org/current/userguide/build_cache.html#build_cache) (Use `gradle_docs(path="userguide/build_cache.html#build_cache")`.) of a task varies between different task instances or cannot be determined until the task is executed by Gradle. You should instead favor annotating the task class itself with [`@CacheableTask`](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/CacheableTask.html) (Use `gradle_docs(path="javadoc/org/gradle/api/tasks/CacheableTask.html")`.) annotation for any task that is *always* cacheable. Likewise, the [`@DisableCachingByDefault`](https://docs.gradle.org/current/javadoc/org/gradle/work/DisableCachingByDefault.html) (Use `gradle_docs(path="javadoc/org/gradle/work/DisableCachingByDefault.html")`.) should be used to always disable caching for all instances of a task type.  

## Explanation
Annotating a task type will ensure that *each task instance* of that type is properly understood by Gradle to be cacheable (or not cacheable). This removes the need to remember to configure each of the task instances separately in build scripts.  
Using the annotations also *documents* the intended cacheability of the task type within its own source, appearing in Javadoc and making the task's behavior clear to other developers without requiring them to inspect each task instance's configuration. It is also slightly more efficient than running a test to determine cacheability.  
Remember that only tasks that produce reproducible and relocatable output should be marked as `@CacheableTask`.  

## Example
### Don't Do This
If you want to reuse the output of a task, you shouldn't do this:  
build.gradle.kts  

```kotlin
abstract class BadCalculatorTask : DefaultTask() { (1)
    @get:Input
    abstract val first: Property<Int>

    @get:Input
    abstract val second: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val result = first.get() + second.get()
        logger.lifecycle("Result: $result")
        outputFile.get().asFile.writeText(result.toString())
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register<BadCalculatorTask>("addBad1") {
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("badOutput.txt")
    outputs.cacheIf { true } (2)
}

tasks.register<BadCalculatorTask>("addBad2") { (3)
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("badOutput2.txt")
}
```

build.gradle  

```groovy
abstract class BadCalculatorTask extends DefaultTask {
    @Input
    abstract Property<Integer> getFirst()

    @Input
    abstract Property<Integer> getSecond()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void run() {
        def result = first.get() + second.get()
        logger.lifecycle("Result: " + result)
        outputFile.get().asFile.write(result.toString())
    }
}

tasks.register("clean", Delete) {
    delete layout.buildDirectory
}

tasks.register("addBad1", BadCalculatorTask) {
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("badOutput.txt")
    outputs.cacheIf { true }
}

tasks.register("addBad2", BadCalculatorTask) {
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("badOutput2.txt")
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Define a Task** : The `BadCalculatorTask` type is deterministic and produces relocatable output, but is not annotated.                                                                                              |
| **2** | **Mark the Task Instance as Cacheable**: This example shows how to mark a specific task instance as cacheable.                                                                                                        |
| **3** | **Forget to Mark a Task Instance as Cacheable** : Unfortunately, the `addBad2` instance of the `BadCalculatorTask` type is not marked as cacheable, so it will not be cached, despite behaving the same as `addBad1`. |

### Do This Instead
As this task meets the criteria for cacheability (we can imagine a more complex calculation in the `@TaskAction` that would benefit from automatic work avoidance via caching), you should mark the *task type itself* as cacheable like this:  
build.gradle.kts  

```kotlin
@CacheableTask (1)
abstract class GoodCalculatorTask : DefaultTask() {
    @get:Input
    abstract val first: Property<Int>

    @get:Input
    abstract val second: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val result = first.get() + second.get()
        logger.lifecycle("Result: $result")
        outputFile.get().asFile.writeText(result.toString())
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register<GoodCalculatorTask>("addGood1") { (2)
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("goodOutput.txt")
}

tasks.register<GoodCalculatorTask>("addGood2") {
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("goodOutput2.txt")
}
```

build.gradle  

```groovy
@CacheableTask (1)
abstract class GoodCalculatorTask extends DefaultTask {
    @Input
    abstract Property<Integer> getFirst()

    @Input
    abstract Property<Integer> getSecond()

    @OutputFile
    abstract RegularFileProperty getOutputFile()

    @TaskAction
    void run() {
        def result = first.get() + second.get()
        logger.lifecycle("Result: " + result)
        outputFile.get().asFile.write(result.toString())
    }
}

tasks.register("clean", Delete) {
    delete layout.buildDirectory
}

tasks.register("addGood1", GoodCalculatorTask) {
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("goodOutput.txt")
}

tasks.register("addGood2", GoodCalculatorTask) { (2)
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("goodOutput2.txt")
}
```

|-------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Annotate the Task Type** : Applying the `@CacheableTask` to a task type informs Gradle that instances of this task should *always* be cached. |
| **2** | **Nothing Else Needs To Be Done**: When we register task instances, nothing else needs to be done - Gradle knows to cache them.                 |

## References
* [Caching Tasks](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:caching_tasks) (Use `gradle_docs(path="userguide/more_about_tasks.html#sec:caching_tasks")`.)

* [Cacheable Tasks](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching_details) (Use `gradle_docs(path="userguide/build_cache.html#sec:task_output_caching_details")`.)

* [Non-cacheable Tasks](https://docs.gradle.org/current/userguide/build_cache_concepts.html#non_cacheable_tasks) (Use `gradle_docs(path="userguide/build_cache_concepts.html#non_cacheable_tasks")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
