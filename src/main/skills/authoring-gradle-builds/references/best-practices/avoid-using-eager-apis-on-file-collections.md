<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: abeecfdee62418f4ddfa0cf91afabf14c6703fae6f71f25e336e400873d87cc5
-->
# Avoid using eager APIs on File Collections
When working with Gradle's file collection types, be careful to avoid triggering dependency resolution during the configuration phase.  

## Explanation
Gradle's [`Configuration` (Use `gradle_docs(path="javadoc/org/gradle/api/artifacts/Configuration.md")`.) and [`FileCollection` (Use `gradle_docs(path="javadoc/org/gradle/api/file/FileCollection.md")`.) types extend the JDK's `Collection<File>` interface.  
However, calling some available methods from this interface---such as `.size()`, `.isEmpty()`, `getFiles()`, `asPath()`, or `.toList()`---on these Gradle types will implicitly trigger resolution of their dependencies. The same is possible using Kotlin stdlib collection extension methods or Groovy GDK collection extensions. Converting a `Configuration` to a `Set<File>` also discards any implicit task dependencies it carries.  
You should avoid using these methods when configuring your build. Instead, use the methods defined directly on the Gradle interfaces - this is a necessary *first step* towards preventing eager resolutions. Be sure to use [lazy types and APIs (Use `gradle_docs(path="userguide/lazy_configuration.md")`.) that defer resolution to wire task dependencies and inputs correctly. Some methods that cause resolution are not obvious. Be sure to check the actual behavior when using configurations in an atypical way.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
abstract class FileCounterTask: DefaultTask() {
    @get:InputFiles
    abstract val countMe: ConfigurableFileCollection

    @TaskAction
    fun countFiles() {
        logger.lifecycle("Count: " + countMe.files.size)
    }
}

tasks.register<FileCounterTask>("badCountingTask") {
    if (!configurations.runtimeClasspath.get().isEmpty()) { (1)
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
        countMe.from(configurations.runtimeClasspath)
    }
}

tasks.register<FileCounterTask>("badCountingTask2") {
    val files = configurations.runtimeClasspath.get().files (2)
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}

tasks.register<FileCounterTask>("badCountingTask3") {
    val files = configurations.runtimeClasspath.get() + layout.projectDirectory.file("extra.txt") (3)
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}

tasks.register<Zip>("badZippingTask") { (4)
    if (!configurations.runtimeClasspath.get().isEmpty()) {
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
        from(configurations.runtimeClasspath)
    }
}
```

build.gradle  

```groovy
abstract class FileCounterTask extends DefaultTask {
    @InputFiles
    abstract ConfigurableFileCollection getCountMe();

    @TaskAction
    void countFiles() {
        logger.lifecycle("Count: " + countMe.files.size())
    }
}

tasks.register("badCountingTask", FileCounterTask) {
    if (!configurations.runtimeClasspath.isEmpty()) { (1)
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.state == RESOLVED))
        countMe.from(configurations.runtimeClasspath)
    }
}

tasks.register("badCountingTask2", FileCounterTask) {
    def files = configurations.runtimeClasspath.files (2)
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.state == RESOLVED))
}

tasks.register("badCountingTask3", FileCounterTask) {
    def files = configurations.runtimeClasspath + layout.projectDirectory.file("extra.txt") (3)
    countMe.from(files)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.state == RESOLVED))
}

tasks.register("badZippingTask", Zip) { (4)
    if (!configurations.runtimeClasspath.isEmpty()) {
        logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.state == RESOLVED))
        from(configurations.runtimeClasspath)
    }
}
```

|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **`isEmpty()` causes resolution** : Many seemingly harmless Collection API methods like `isEmpty()` cause Gradle to resolve dependencies.                                                                                                                                                                     |
| **2** | **Accessing files directly** : Using `getFiles()` to access the files in a `Configuration` will also cause Gradle to resolve the file collection.                                                                                                                                                             |
| **3** | **Adding a file via plus operator** : Using the plus operator will force the `runtimeClasspath` configuration to be resolved implicitly. The implementation of `Configuration` doesn't override the plus operator for regular files, therefore it falls back to using the eager API, which causes resolution. |
| **4** | **Be careful with indirect inputs** : Some built-in tasks, for example subtypes of `AbstractCopyTask` like `Zip`, allow adding inputs indirectly and can have the same problems.                                                                                                                              |

### Do This Instead
To avoid issues, always defer resolution until the execution phase. Use APIs that support lazy evaluation.  
build.gradle.kts  

```kotlin
abstract class FileCounterTask: DefaultTask() {
    @get:InputFiles
    abstract val countMe: ConfigurableFileCollection

    @TaskAction
    fun countFiles() {
        logger.lifecycle("Count: " + countMe.files.size)
    }
}

tasks.register<FileCounterTask>("goodCountingTask") {
    countMe.from(configurations.runtimeClasspath) (1)
    countMe.from(layout.projectDirectory.file("extra.txt"))
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.get().state == RESOLVED))
}
```

build.gradle  

```groovy
abstract class FileCounterTask extends DefaultTask {
    @InputFiles
    abstract ConfigurableFileCollection getCountMe();

    @TaskAction
    void countFiles() {
        logger.lifecycle("Count: " + countMe.files.size())
    }
}

tasks.register("goodCountingTask", FileCounterTask) {
    countMe.from(configurations.runtimeClasspath) (1)
    countMe.from(layout.projectDirectory.file("extra.txt")) (2)
    logger.lifecycle("Resolved: " + (configurations.runtimeClasspath.state == RESOLVED))
}
```

|-------|-------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Add configurations to Task properties or Specs directly**: This will defer resolution until the task is executed.           |
| **2** | **Add files to Specs separately**: This allows combining files with file collections without triggering implicit resolutions. |

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
