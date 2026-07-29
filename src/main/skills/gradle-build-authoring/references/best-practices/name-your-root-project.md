# Name Your Root Project
Always name your root project in the `settings.gradle(.kts)` file.  

## Explanation
While an empty `settings.gradle(.kts)` file is enough to create a multi-project build, you should always set the `rootProject.name` property.  
By default, the root project's name is taken from the directory containing the build. This can be problematic if the directory name contains spaces, Gradle logical path separators, or other special characters. It also makes task paths dependent on the directory name, rather than being reliably defined.  
Explicitly setting the root project's name ensures consistency across environments. Project names appear in error messages, logs, and reports, and builds often run on different machines, such as CI servers. Builds may execute on a variety of machines or environments, such as CI servers, and should report the same root project name anywhere to make the project more comprehensible.  

## Example
### Don't Do This
settings.gradle.kts  

```kotlin
// Left empty
```

settings.gradle  

```groovy
// Left empty
```

In this build, the settings file is empty and the root project has no explicit name. Running the `projects` report shows that Gradle assigns an implicit name to the root project, derived from the build's current directory.  
Unfortunately that name varies based on where the project currently lives. For example, if the project is checked out into a directory named `some-directory-name`, the output of `./gradlew projects` will look like this:  

```text
> Task :projects

# Projects:
# Root project 'some-directory-name'
```

### Do This Instead
settings.gradle.kts  

```kotlin
rootProject.name = "my-example-project"
```

settings.gradle  

```groovy
rootProject.name = "my-example-project"
```

In this build, the root project is explicitly named. The explicit name `my-example-project` will be used in all reports, logs, and error messages. Regardless of where the project lives, the output of `./gradlew projects` will look like this:  
nameYourRootProject-do.out  

```out
> Task :projects

# Projects:
# Root project 'my-example-project'
Project hierarchy:

Root project 'my-example-project'
No sub-projects

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :tasks

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

## References
* [Naming recommendations](https://docs.gradle.org/current/userguide/multi_project_builds.html#sec:naming_recommendations) (Use `gradle_docs(path="userguide/multi_project_builds.html#sec:naming_recommendations")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
