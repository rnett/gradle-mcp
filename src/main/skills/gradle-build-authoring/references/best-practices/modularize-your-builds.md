# Modularize Your Builds
Modularize your builds by splitting your code into multiple projects.  

## Explanation
Splitting your build's source into multiple Gradle projects (modules) is essential for leveraging Gradle's automatic work avoidance and parallelization features. When a source file changes, Gradle only recompiles the affected projects. If all your sources reside in a single project, Gradle can't avoid recompilation and won't be able to run tasks in parallel. Splitting your source into multiple projects can provide additional performance benefits by minimizing each subproject's compilation classpath and ensuring code generating tools such as annotation and symbol processors run only on the relevant files.  
Do this *soon*. Don't wait until you hit some arbitrary number of source files or classes to do this, instead structure your build into multiple projects from the start using whatever natural boundaries exist in your codebase.  
Exactly how to best split your source varies with every build, as it depends on the particulars of that build. Here are some common patterns we found that can work well and make cohesive projects:  
* API vs. Implementation

* Front-end vs. Back-end

* Core business logic vs. UI

* Vertical slices (e.g., feature modules each containing UI + business logic)

* Inputs to source generation vs. their consumers

* Or simply closely related classes.

Ultimately, the specific scheme matters less than ensuring that your build is split logically and consistently.  
Expanding a build to hundreds of projects is common, and Gradle is designed to scale to this size and beyond. In the extreme, tiny projects containing only a class or two are probably counterproductive. However, you should typically err on the side of adding more projects rather than fewer.  

## Example
### Don't Do This
A common way to structure new builds  

```kotlin
├── app // This project contains a mix of classes
│    ├── build.gradle.kts
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── CommonsUtil.java
│                        └── GuavaUtil.java
│                        └── Main.java
│                        └── Util.java
├── settings.gradle.kts
```

A common way to structure new builds  

```groovy
├── app // This project contains a mix of classes
│    ├── build.gradle
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── CommonsUtil.java
│                        └── GuavaUtil.java
│                        └── Main.java
│                        └── Util.java
├── settings.gradle
```

settings.gradle.kts  

```kotlin
include("app") (1)
```

settings.gradle  

```groovy
include("app") (1)
```

build.gradle.kts  

```kotlin
plugins {
    application (2)
}

dependencies {
    implementation("com.google.guava:guava:31.1-jre") (3)
    implementation("commons-lang:commons-lang:2.6")
}

application {
    mainClass = "org.example.Main"
}
```

build.gradle  

```groovy
plugins {
    id 'application' (2)
}

dependencies {
    implementation 'com.google.guava:guava:31.1-jre' (3)
    implementation 'commons-lang:commons-lang:2.6'
}

application {
    mainClass = "org.example.Main"
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | This build contains only a single project (in addition to the root project) that contains all the source code. If there is any change to any source file, Gradle will have to recompile and rebuild everything. While incremental compilation will help (especially in this simplified example) this is still less efficient then avoidance. Gradle also won't be able to run any tasks in parallel, since all these tasks are in the same project, so this design won't scale nicely. |
| **2** | As there is only a single project in this build, the `application` plugin must be applied here. This means that the `application` plugin will affect all source files in the build, even those which have no need for it.                                                                                                                                                                                                                                                              |
| **3** | Likewise, the dependencies here are only needed by each particular implmentation of util. There's no need for the implementation using Guava to have access to the Commons library, but it does because they are all in the same project. This also means that the classpath for each subproject is much larger than it needs to be, which can lead to longer build times and other confusion.                                                                                         |

### Do This Instead
A better way to structure this build  

```kotlin
├── app
│    ├── build.gradle.kts
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── Main.java
├── settings.gradle.kts
├── util
│    ├── build.gradle.kts
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── Util.java
├── util-commons
│    ├── build.gradle.kts
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── CommonsUtil.java
└── util-guava
    ├── build.gradle.kts
    └── src
        └── main
            └── java
                └── org
                    └── example
                        └── GuavaUtil.java
```

A better way to structure this build  

```groovy
├── app // App contains only the core application logic
│    ├── build.gradle
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── Main.java
├── settings.gradle
├── util // Util contains only the core utility logic
│    ├── build.gradle
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── Util.java
├── util-commons // One particular implementation of util, using Apache Commons
│    ├── build.gradle
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        └── CommonsUtil.java
└── util-guava // Another implementation of util, using Guava
    ├── build.gradle
    └── src
        └── main
            └── java
                └── org
                    └── example
                        └── GuavaUtil.java
```

settings.gradle.kts  

```kotlin
include("app") (1)
include("util")
include("util-commons")
include("util-guava")
```

settings.gradle  

```groovy
include("app") (1)
include("util")
include("util-commons")
include("util-guava")
```

build.gradle.kts  

```kotlin
// This is the build.gradle file for the app module

plugins {
    application (2)
}

dependencies { (3)
    implementation(project(":util-guava"))
    implementation(project(":util-commons"))
}

application {
    mainClass = "org.example.Main"
}
```

build.gradle  

```groovy
// This is the build.gradle file for the app module

plugins {
    id "application" (2)
}

dependencies { (3)
    implementation project(":util-guava")
    implementation project(":util-commons")
}

application {
    mainClass = "org.example.Main"
}
```

build.gradle.kts  

```kotlin
// This is the build.gradle file for the util-commons module

plugins { (4)
    `java-library`
}

dependencies { (5)
    api(project(":util"))
    implementation("commons-lang:commons-lang:2.6")
}
```

build.gradle  

```groovy
// This is the build.gradle file for the util-commons module

plugins { (4)
    id "java-library"
}

dependencies { (5)
    api project(":util")
    implementation "commons-lang:commons-lang:2.6"
}
```

build.gradle.kts  

```kotlin
// This is the build.gradle file for the util-guava module

plugins {
    `java-library`
}

dependencies {
    api(project(":util"))
    implementation("com.google.guava:guava:31.1-jre")
}
```

build.gradle  

```groovy
// This is the build.gradle file for the util-guava module

plugins {
    id "java-library"
}

dependencies {
    api project(":util")
    implementation "com.google.guava:guava:31.1-jre"
}
```

|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | This build logically splits the source into multiple projects. Each project can be built independently, and Gradle can run tasks in parallel. This means that if you change a single source file in one of the projects, Gradle will only need to recompile and rebuild that project, not the entire build. |
| **2** | The `application` plugin is only applied to the `app` project, which is the only project that needs it.                                                                                                                                                                                                     |
| **3** | Each project only adds the dependencies it needs. This means that the classpath for each subproject is much smaller, which can lead to faster build times and less confusion.                                                                                                                               |
| **4** | Each project only adds the specific plugins it needs.                                                                                                                                                                                                                                                       |
| **5** | Each project only adds the dependencies it needs. Projects can effectively use [API vs. Implementation separation](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation) (Use `gradle_docs(path="userguide/java_library_plugin.html#sec:java_library_separation")`.).                                                                                                                                   |

## References
* [Structuring Projects with Gradle](https://docs.gradle.org/current/userguide/multi_project_builds.html#multi_project_builds) (Use `gradle_docs(path="userguide/multi_project_builds.html#multi_project_builds")`.)

* [Organizing Gradle Projects](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:settings_file) (Use `gradle_docs(path="userguide/organizing_gradle_projects.html#sec:settings_file")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
