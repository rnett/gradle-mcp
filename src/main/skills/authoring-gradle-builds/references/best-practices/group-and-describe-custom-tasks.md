# Group and Describe custom Tasks
When defining custom task types or registering ad-hoc tasks, always set a clear `group` and `description`.  

## Explanation
A good group name is short, lowercase, and reflects the purpose or domain of the task. For example: `documentation`, `verification`, `release`, or `publishing`.  
Before creating a new group, look for an existing group name that aligns with your task's intent. It's often better to reuse an established category to keep the task output organized and familiar to users.  
This information is used in the [Tasks Report (Use `gradle_docs(path="userguide/command_line_interface.md")`.) (shown via `./gradlew tasks`) to group and describe available tasks in a readable format.  
Providing a group and description ensures that your tasks are:  
* Displayed clearly in the report

* Categorized appropriately

* Understandable to other users (and to your future self)

|---|------------------------------------------------------------------------------------------------------------------------------------|
|   | Tasks with no group are hidden from the [Tasks Report (Use `gradle_docs(path="userguide/command_line_interface.md")`.) unless `--all` is specified. |

## Example
### Don't Do This
Tasks without a group appear under the "other" category in `./gradlew tasks --all` output, making them harder to locate:  
app/build.gradle.kts  

```kotlin
tasks.register("generateDocs") {
    // Build logic to generate documentation
}
```

app/build.gradle  

```groovy
tasks.register('generateDocs') {
    // Build logic to generate documentation
}
```

```text
$ gradlew :app:tasks --all

# > Task :app:tasks
# Tasks runnable from project ':app'
# Other tasks
compileJava - Compiles main Java source.
compileTestJava - Compiles test Java source.
generateDocs
processResources - Processes main resources.
processTestResources - Processes test resources.
startScripts - Creates OS specific scripts to run the project as a JVM application.
```

### Do this Instead
When defining custom tasks, always assign a clear `group` and `description`:  
app/build.gradle.kts  

```kotlin
tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates project documentation from source files."
    // Build logic to generate documentation
}
```

app/build.gradle  

```groovy
tasks.register('generateDocs') {
    group = 'documentation'
    description = 'Generates project documentation from source files.'
    // Build logic to generate documentation
}
```

```text
$ gradlew :app:tasks --all

# > Task :app:tasks
# Tasks runnable from project ':app'
# Documentation tasks
generateDocs - Generates project documentation from source files.
javadoc - Generates Javadoc API documentation for the 'main' feature.
```

## References
* [Task Group and Description (Use `gradle_docs(path="userguide/more_about_tasks.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
