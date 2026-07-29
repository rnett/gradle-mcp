# Avoid Unintentionally Creating Empty Projects
When using a hierarchical directory structure to organize your Gradle projects, make sure to avoid unintentionally creating empty projects in your build.  

## Explanation
When you use the [Settings.include()](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.initialization/-settings/include.html) (Use `gradle_docs(path="kotlin-dsl/gradle/org.gradle.api.initialization/-settings/include.html")`.) method to include a project in your Grade settings file, you typically include projects by supplying the directory name like `include("featureA")`. This usage assumes that `featureA` is located at the root of your build.  
You can include projects located in nested subdirectories by specifying their full project path using `:` as a separator between path segments. For instance, if project `search` was located in a subdirectory named `features`, itself located in a subdirectory named `subs`, you could call `include(":subs:features:search")` to include it.  
Nesting projects in a sensible hierarchical directory structure is common practice in larger Gradle builds. This approach helps organize large builds and improves comprehensibility, compared to placing all projects directly under the build's root.  
However, without further configuration, Gradle will create empty projects for each element in every hierarchical path, even if some of those directories do not contain actual Gradle projects. In the example above, Gradle will create a project named `:subs`, a project named `:subs:features`, and a project named `:subs:features:search`. This behavior is usually not intended, as you likely only want to include the `search` project.  
Unused projects - even if empty - can surprise maintainers, clutter reports, and make your build harder to understand. They also introduce unintended side effects. If you use `allprojects { ...​ }` or `subprojects { ...​ }`, plugins and configuration blocks will apply to every project, including the empty ones. This can degrade build performance. Additionally, invoking tasks on deeply nested projects requires using the full project path, such as `gradle :subs:features:search:build`, instead of the shorter `gradle :search:build`.  
To avoid these downsides when using a hierarchical project structure, you can provide a flat name when including the project and explicitly set the [Project.projectDir](https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api/-project/get-project-dir.html) (Use `gradle_docs(path="kotlin-dsl/gradle/org.gradle.api/-project/get-project-dir.html")`.) property for any projects located in nested directories:  

```kotlin
include(':my-web-module')
project(':my-web-module').projectDir = file("subs/web/my-web-module")
```

This will prevent Gradle from creating empty projects for each element of the project's path.  

|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | Always use an identical *logical* project **name** and *physical* project **location** to avoid confusion. Don't include a project named `:search` and locate it at `features/ui/default-search-toolbar`, as this will lead to confusion about the location of the project. Instead, locate this project at `features/ui/search`. |

You should avoid unnecessarily deep directory structures. For builds containing only a few projects, it's usually better to keep the structure flat by placing all projects at the root of the build. This eliminates the need to explicitly set `projectDir`. Within the context of a particular build, the *pathless* project name should clearly indicate where the project is located. You can also run the [projects report](https://docs.gradle.org/current/userguide/project_report_plugin.html#project_report_plugin) (Use `gradle_docs(path="userguide/project_report_plugin.html#project_report_plugin")`.) for more information about the projects in your build and their locations.  
If you find yourself facing ambiguity about project locations, consider simplifying the directory layout by flattening the structure, or using longer, more descriptive project names.  

## Example
### Don't Do This
```kotlin
├── settings.gradle.kts
├── app/ (1)
│   ├── build.gradle.kts
│   └── src/
└── subs/ (2)
    └── web/ (3)
        ├── my-web-module/ (4)
            ├── src/
            └── build.gradle.kts
```

```groovy
├── settings.gradle
├── app/ (1)
│   ├── build.gradle
│   └── src/
└── subs/ (2)
    └── web/ (3)
        ├── my-web-module/ (4)
            ├── src/
            └── build.gradle
```

|-------|--------------------------------------------------------------------------------------------------------------------|
| **1** | A project named `app` located at the root of the build                                                             |
| **2** | A directory named `subs` that is **not** intended to represent a Gradle project, but is used to organize the build |
| **3** | Another organizational directory **not** intended to represent a Gradle project                                    |
| **4** | A Gradle project named `my-web-module` that **should** be included in the build                                    |

settings.gradle.kts  

```kotlin
include(":app") (1)
include(":subs:web:my-web-module") (2)
```

settings.gradle  

```groovy
include(":app") (1)
include(":subs:web:my-web-module") (2)
```

|-------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | Including the `app` project located at the root of the build requires no additional configuration                                                    |
| **2** | Including a project named `:subs:my-web-module` located in a nested subdirectory causes Gradle to create empty projects for each element of the path |

avoidEmptyProjects-avoid.out  

```out
> Task :projects

# Projects:
# Root project 'avoidEmptyProjects-avoid'
Location: /home/user/gradle/samples

Project hierarchy:

Root project 'avoidEmptyProjects-avoid'
+--- Project ':app'
\--- Project ':subs'
     \--- Project ':subs:web'
          \--- Project ':subs:web:my-web-module'

Project locations:

project ':app' - /app
project ':subs' - /subs
project ':subs:web' - /subs/web
project ':subs:web:my-web-module' - /subs/web/my-web-module

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :app:tasks

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

The output of running the `projects` report on the above build shows that Gradle created empty projects for `:subs` and `:subs:web`.  

### Do This Instead
settings.gradle.kts  

```kotlin
include(":app")

include(":my-web-module")
project(":my-web-module").projectDir = file("subs/web/my-web-module") (1)
```

settings.gradle  

```groovy
include(":app")

include(":my-web-module")
project(":my-web-module").projectDir = file("subs/web/my-web-module") (1)
```

|-------|---------------------------------------------------------------------------------------------------------------------------------|
| **1** | After including the `:subs:web:my-web-module` project, its `projectDir` property is set to the physical location of the project |

avoidEmptyProjects-do.out  

```out
> Task :projects

# Projects:
# Root project 'avoidEmptyProjects-do'
Location: /home/user/gradle/samples

Project hierarchy:

Root project 'avoidEmptyProjects-do'
+--- Project ':app'
\--- Project ':my-web-module'

Project locations:

project ':app' - /app
project ':my-web-module' - /subs/web/my-web-module

To see a list of the tasks of a project, run gradle <project-path>:tasks
For example, try running gradle :app:tasks

BUILD SUCCESSFUL in 0s
1 actionable task: 1 executed
```

The output of running the `projects` report on the above build shows that now Gradle only creates the intended projects for this build.  
You can also now invoke tasks on the `my-web-module` project using the shorter name `:my-web-module` like `gradle :my-web-module:build`, instead of `gradle :subs:web:my-web-module:build`.  

## References
* [Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html#multi_project_builds) (Use `gradle_docs(path="userguide/multi_project_builds.html#multi_project_builds")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
