<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 8f953ff5da05f087f88732409a10a4296aecba7c68b214dd7b15d14623be029f
-->
# Do Not Put Source Files in the Root Project
Do not put source files in your root project; instead, put them in a separate project.  

## Explanation
The root project is a special [Project (Use `gradle_docs(path="kotlin-dsl/gradle/org.gradle.api/-project/index.md")`.) in Gradle that serves as the entry point for your build.  
It is the place to configure some settings and conventions that apply globally to the entire build, that are not configured via [Settings (Use `gradle_docs(path="kotlin-dsl/gradle/org.gradle.api.initialization/-settings/index.md")`.). For example, you can *declare* (but not apply) plugins here to ensure the same plugin version is consistently available across all projects and define other configurations shared by all projects within the build.  

|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | Be careful not to apply plugins unnecessarily in the root project - many plugins only affect source code and should only be applied to the projects that contain source code. |

The root project should not be used for source files, instead they should be located in a separate Gradle project.  
Setting up your build like this from the start will also make it easier to add new projects as your build grows in the future.  

## Example
### Don't Do This
A common way to structure new builds  

```kotlin
├── build.gradle.kts // Applies the `java-library` plugin to the root project
├── settings.gradle.kts
└── src // This directory shouldn't exist
    └── main
        └── java
            └── org
                └── example
                    └── MyClass1.java
```

A common way to structure new builds  

```groovy
├── build.gradle // Applies the `java-library` plugin to the root project
├── settings.gradle
└── src // This directory shouldn't exist
    └── main
        └── java
            └── org
                └── example
                    └── MyClass1.java
```

build.gradle.kts  

```kotlin
plugins { (1)
    `java-library`
}
```

build.gradle  

```groovy
plugins {
    id 'java-library' (1)
}
```

|-------|-------------------------------------------------------------------------------------------------------------------|
| **1** | The `java-library` plugin is applied to the root project, as there are Java source files are in the root project. |

### Do This Instead
A better way to structure new builds  

```kotlin
├── core
│    ├── build.gradle.kts // Applies the `java-library` plugin to only the `core` project
│    └── src // Source lives in a "core" (sub)project
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── MyClass1.java
└── settings.gradle.kts
```

A better way to structure new builds  

```groovy
├── core
│    ├── build.gradle // Applies the `java-library` plugin to only the `core` project
│    └── src // Source lives in a "core" (sub)project
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── MyClass1.java
└── settings.gradle
```

settings.gradle.kts  

```kotlin
include("core") (1)
```

settings.gradle  

```groovy
include("core") (1)
```

build.gradle.kts  

```kotlin
// This is the build.gradle.kts file for the core module

plugins { (2)
    `java-library`
}
```

build.gradle  

```groovy
// This is the build.gradle file for the core module

plugins { (2)
    id 'java-library'
}
```

|-------|--------------------------------------------------------------------------------------------------------|
| **1** | The root project exists only to configure the build, informing Gradle of a (sub)project named `core`.  |
| **2** | The `java-library` plugin is only applied to the `core` project, which contains the Java source files. |

## References
* [Structuring Projects with Gradle (Use `gradle_docs(path="userguide/multi_project_builds.md")`.)

* [Organizing Gradle Projects (Use `gradle_docs(path="userguide/organizing_gradle_projects.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
