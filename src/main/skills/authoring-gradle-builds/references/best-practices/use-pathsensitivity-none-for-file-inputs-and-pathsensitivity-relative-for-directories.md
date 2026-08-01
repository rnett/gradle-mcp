<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 9210bc3c8cf898de6839b4695e74511d10d844ec550b1cf6badc6a28b46ce902
-->
# Use `@PathSensitivity.NONE` for file inputs and `@PathSensitivity.RELATIVE` for directories
Use [`@PathSensitivity.NONE` (Use `gradle_docs(path="javadoc/org/gradle/api/tasks/PathSensitivity.md")`.) for file inputs and [`@PathSensitivity.RELATIVE` (Use `gradle_docs(path="javadoc/org/gradle/api/tasks/PathSensitivity.md")`.) for directory inputs.  

## Explanation
Tasks should generally care about the **contents** of their input files, not their location on disk.  
When annotating file-based input properties (for example, `@InputFile` or `@InputFiles` collections), use `@PathSensitivity.NONE`. This tells Gradle to ignore the path and only consider the file contents when determining whether a task is up-to-date.  
For directory-based inputs (for example, `@InputDirectory` or `@InputFiles` collections), use `@PathSensitivity.RELATIVE`. This tells Gradle to also consider only the name of the directory (ignoring its absolute location) and to relativize the paths of all files within that directory to it when doing up-to-date checks.  
Using `PathSensitivity.NAME_ONLY` or `@PathSensitivity.ABSOLUTE` is generally incorrect.  
`PathSensitivity.NAME_ONLY` tells Gradle to consider a file's name in addition to its contents, which is rarely useful.  
`@PathSensitivity.ABSOLUTE` tells Gradle to consider a file's complete absolute path. This prevents [Build Cache (Use `gradle_docs(path="userguide/build_cache.md")`.) and [Configuration Cache (Use `gradle_docs(path="userguide/configuration_cache.md")`.) hits across different machines or checkout locations, making your build non-relocatable. It can also lead to confusing behavior where the same build produces different task outcomes when run from different directories. If no `@PathSensitive` annotation is provided, `PathSensitivity.ABSOLUTE` is the default.  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
abstract class AnimalSearchTask : DefaultTask() {
    @get:Input
    abstract val find: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE) (1)
    abstract val candidatesFile: RegularFileProperty

    @get:OutputFile
    abstract val resultsFile: RegularFileProperty

    @TaskAction
    fun search() {
        if (candidatesFile.get().getAsFile().readLines().contains(find.get())) {
            val msg = "Found a " + find.get() + "!"
            getLogger().lifecycle(msg)
            resultsFile.get().asFile.writeText(msg)
        }
    }
}

val useAlternateInput = providers.gradleProperty("useAlternateInput").isPresent()

val copyTask = tasks.register<Copy>("copy") {
    from(layout.projectDirectory.file("candidates.txt"))
    destinationDir = (if (useAlternateInput) { layout.buildDirectory.dir("alternateSearchInput") } else { layout.buildDirectory.dir("searchInput") }).get().asFile
}

tasks.register<AnimalSearchTask>("search") {
    find = "cat"
    candidatesFile.fileProvider(copyTask.map { File(it.destinationDir, "candidates.txt") })
    resultsFile = layout.buildDirectory.file("searchOutput/results.txt")
    dependsOn(copyTask)
}
```

build.gradle  

```groovy
abstract class AnimalSearchTask extends DefaultTask {
    @Input
    abstract Property<String> getFind()

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE) (1)
    abstract RegularFileProperty getCandidatesFile()

    @OutputFile
    abstract RegularFileProperty getResultsFile()

    @TaskAction
    void search() {
        if (candidatesFile.get().getAsFile().readLines().contains(find.get())) {
            def msg = "Found a " + find.get() + "!"
            getLogger().lifecycle(msg)
            resultsFile.get().asFile.text = msg
        }
    }
}

def useAlternateInput = providers.gradleProperty("useAlternateInput").isPresent()

def copyTask = tasks.register("copy", Copy) {
    from(layout.projectDirectory.file("candidates.txt"))
    destinationDir = (useAlternateInput ? layout.buildDirectory.dir("alternateSearchInput") : layout.buildDirectory.dir("searchInput")).get().asFile (2)
}

tasks.register("search", AnimalSearchTask) {
    find = "cat"
    candidatesFile.fileProvider(copyTask.map { new File(it.destinationDir, "candidates.txt") }) (3)
    resultsFile = layout.buildDirectory.file("searchOutput/results.txt")
    dependsOn(copyTask)
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | The `AnimalSearchTask` task type uses a file input property annotated with `@PathSensitivity.ABSOLUTE`. This means that the absolute path of the input file is used to determine if the task is `UP-TO-DATE` or if it can be loaded from cache. Yet the path is irrelevant for the operation of the task's `@TaskAction`, which only cares about file contents. |
| **2** | The `copy` task will move the *exact same* `candidates.txt` to different destination directories, depending on if the `useAlternateInput` project property is set.                                                                                                                                                                                              |
| **3** | The `search` task is wired to use as input whatever the file the `copy` task moved to its `destinationDir`. Despite the contents of the file being the same, when enabling the `-PuseAlternateInput` after a successful build, the `search` task will be out-of-date due to its different directory, and the search will be rerun.                              |

### Do this Instead
build.gradle.kts  

```kotlin
@get:InputFile
@get:PathSensitive(PathSensitivity.NONE) (1)
abstract val candidatesFile: RegularFileProperty
```

build.gradle  

```groovy
@InputFile
@PathSensitive(PathSensitivity.NONE) (1)
abstract RegularFileProperty getCandidatesFile()
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Everything remains the same, except that the input property is now annotated with `@PathSensitivity.NONE`. Only the contents of this input file matter to this task. When the `search` task is rerun with `-PuseAlternateInput`, it remains `UP-TO-DATE`. |

## References
* [Input Relocatability (Use `gradle_docs(path="userguide/build_cache_concepts.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
