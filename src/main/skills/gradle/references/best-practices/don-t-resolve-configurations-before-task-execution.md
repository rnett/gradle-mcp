# Don't resolve Configurations before Task Execution
Resolving configurations before the task execution phase can lead to incorrect results and slower builds.  

## Explanation
Resolving a configuration - either directly via calling its [`resolve()`](https://docs.gradle.org/current/javadoc/org/gradle/api/artifacts/Configuration.html#resolve() (Use `gradle_docs(path="javadoc/org/gradle/api/artifacts/Configuration.html#resolve(")`.)) method or indirectly via accessing its set of artifacts - returns a set of files that does not preserve references to the tasks that produced those files.  
Configurations *are* file collections and can be added to `@InputFiles` properties on other tasks. It is important to do this correctly to avoid breaking automatic task dependency wiring between a consumer task and any tasks that are implicitly required to produce the artifacts being consumed. For example, if a configuration contains a project dependency, Gradle knows that consumers of the configuration must first run any tasks that produce that project's artifacts.  
In addition to correctness concerns, resolving configurations during the configuration phase can slow down the build, even when running unrelated tasks (e.g., `help`) that don't require the resolved dependencies.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
dependencies {
    runtimeOnly(project(":lib")) (1)
}

abstract class BadClasspathPrinter : DefaultTask() {
    @get:InputFiles
    var classpath: Set<File> = emptySet() (2)

    private fun calculateDigest(fileOrDirectory: File): Int {
        require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    fun run() {
        logger.lifecycle(
            classpath.joinToString("\n") {
                val digest = calculateDigest(it) (3)
                "$it#$digest"
            }
        )
    }
}

tasks.register("badClasspathPrinter", BadClasspathPrinter::class) {
    classpath = configurations.named("runtimeClasspath").get().resolve() (4)
}
```

build.gradle  

```groovy
dependencies {
    runtimeOnly(project(":lib")) (1)
}

abstract class BadClasspathPrinter extends DefaultTask {
    @InputFiles
    Set<File> classpath = [] as Set (2)

    protected int calculateDigest(File fileOrDirectory) {
        if (!fileOrDirectory.exists()) {
            throw new IllegalArgumentException("File or directory $fileOrDirectory doesn't exist")
        }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    void run() {
        logger.lifecycle(
            classpath.collect { file ->
                def digest = calculateDigest(file) (3)
                "$file#$digest"
            }.join("\n")
        )
    }
}

tasks.register("badClasspathPrinter", BadClasspathPrinter) {
    classpath = configurations.named("runtimeClasspath").get().resolve() (4)
}
```

|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Add project dependency** : The `:lib` project must be built in order to resolve the runtime classpath successfully.                                                                                                                                                                                                                 |
| **2** | **Declare input property as Set of files** : A simple `Set` input doesn't track task dependencies.                                                                                                                                                                                                                                    |
| **3** | **Dependency artifacts are used to calculate digest**: Artifacts from the already resolved classpath are used to calculate the digest.                                                                                                                                                                                                |
| **4** | **Resolve runtimeClasspath** : The implicit task dependency on `:library:jar` task is lost here when the configuration is resolved prior to task execution. The `lib` project will not be built when the `:app:badClasspathPrinter` task is run, leading to a failure in `calculateDigest` because the `lib.jar` file will not exist. |

### Do This Instead
To avoid issues, always defer resolution to the execution phase by using lazy APIs like [FileCollection](https://docs.gradle.org/current/javadoc/org/gradle/api/file/FileCollection.html) (Use `gradle_docs(path="javadoc/org/gradle/api/file/FileCollection.html")`.).  
build.gradle.kts  

```kotlin
dependencies {
    runtimeOnly(project(":lib")) (1)
}

abstract class GoodClasspathPrinter : DefaultTask() {
    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection (2)

    private fun calculateDigest(fileOrDirectory: File): Int {
        require(fileOrDirectory.exists()) { "File or directory $fileOrDirectory doesn't exist" }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    fun run() {
        logger.lifecycle(
            classpath.joinToString("\n") {
                val digest = calculateDigest(it) (3)
                "$it#$digest"
            }
        )
    }
}

tasks.register("goodClasspathPrinter", GoodClasspathPrinter::class.java) {
    classpath.from(configurations.named("runtimeClasspath")) (4)
}
```

build.gradle  

```groovy
dependencies {
    runtimeOnly(project(":lib")) (1)
}

abstract class GoodClasspathPrinter extends DefaultTask {

    @InputFiles
    abstract ConfigurableFileCollection getClasspath() (2)

    protected int calculateDigest(File fileOrDirectory) {
        if (!fileOrDirectory.exists()) {
            throw new IllegalArgumentException("File or directory $fileOrDirectory doesn't exist")
        }
        return 0 // actual implementation is stripped
    }

    @TaskAction
    void run() {
        logger.lifecycle(
            classpath.collect { file ->
                def digest = calculateDigest(file) (3)
                "$file#$digest"
            }.join("\n")
        )
    }
}

tasks.register("goodClasspathPrinter", GoodClasspathPrinter) {
    classpath.from(configurations.named("runtimeClasspath")) (4)
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Write to a file in the output directory**: This is the same.                                                                                                                                                                                                                                                                                                                                                                          |
| **2** | **Declare input files property as ConfigurableFileCollection**: This lazy collection type will track task dependencies.                                                                                                                                                                                                                                                                                                                 |
| **3** | **Dependency artifacts are resolved to calculate digest**: The classpath will be resolved at execution time to calculate the digest.                                                                                                                                                                                                                                                                                                    |
| **4** | **Configuration is passed to input property directly** : Using `from` causes the configuration to be lazily wired to the input proeprty. The configuration will be resolved when necessary, preserving task dependencies. The output reveals that the `lib` project is now built when the `:app:goodClasspathPrinter` task is run because of the implicit task dependency, and the `lib.jar` file is found when calculating the digest. |

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
