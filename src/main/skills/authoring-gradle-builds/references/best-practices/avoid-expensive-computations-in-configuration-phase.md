<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 32f0181adf42bf6e8f8a07429cb7c79ed361d362b28e5ee89c3f0cb594b3cb35
-->
# Avoid Expensive Computations in Configuration Phase
Avoid expensive computations in the [configuration phase](https://docs.gradle.org/current/userguide/build_lifecycle.html#build_phases) (Use `gradle_docs(path="userguide/build_lifecycle.html#build_phases")`.), instead, move them to task actions.  

## Explanation
In order for Gradle to execute tasks it first needs to build the project task graph. As part of discovering what tasks to include in the task graph, Gradle will configure all the tasks that are directly requested, any task dependencies of the requested tasks, and also any tasks that are not lazily registered. This work is done in the configuration phase.  
Performing expensive or slow operations such as file or network I/O, or CPU-heavy calculations in the configuration phase forces these to run even when they might be unnecessary to complete the requested work of the invoked tasks. It is better to move these operations to task actions so that they run only when required.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
abstract class MyTask : DefaultTask() {
    @get:Input
    lateinit var computationResult: String
    @TaskAction
    fun run() {
        logger.lifecycle(computationResult)
    }
}

fun heavyWork(): String {
    println("Start heavy work")
    Thread.sleep(5000)
    println("Finish heavy work")
    return "Heavy computation result"
}

tasks.register<MyTask>("myTask") {
    computationResult = heavyWork() (1)
}
```

build.gradle  

```groovy
abstract class MyTask extends DefaultTask {
    @Input
    String computationResult
    @TaskAction
    void run() {
        logger.lifecycle(computationResult)
    }
}

String heavyWork() {
    logger.lifecycle("Start heavy work")
    Thread.sleep(5000)
    logger.lifecycle("Finish heavy work")
    return "Heavy computation result"
}

tasks.register("myTask", MyTask) {
    computationResult = heavyWork() (1)
}
```

|-------|----------------------------------------------------------|
| **1** | Performing heavy computation during configuration phase. |

### Do This Instead
build.gradle.kts  

```kotlin
abstract class MyTask : DefaultTask() {
    @TaskAction
    fun run() {
        logger.lifecycle(heavyWork()) (1)
    }

    fun heavyWork(): String {
        logger.lifecycle("Start heavy work")
        Thread.sleep(5000)
        logger.lifecycle("Finish heavy work")
        return "Heavy computation result"
    }
}

tasks.register<MyTask>("myTask")
```

build.gradle  

```groovy
abstract class MyTask extends DefaultTask {
    @TaskAction
    void run() {
        logger.lifecycle(heavyWork()) (1)
    }
    String heavyWork() {
        logger.lifecycle("Start heavy work")
        Thread.sleep(5000)
        logger.lifecycle("Finish heavy work")
        return "Heavy computation result"
    }
}

tasks.register("myTask", MyTask)
```

|-------|-----------------------------------------------------------------------|
| **1** | Performing heavy computation during execution phase in a task action. |

## References
* [lazy configuration](https://docs.gradle.org/current/userguide/lazy_configuration.html#lazy_configuration) (Use `gradle_docs(path="userguide/lazy_configuration.html#lazy_configuration")`.)

* [Build Lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html#build_lifecycle_reference) (Use `gradle_docs(path="userguide/build_lifecycle.html#build_lifecycle_reference")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
