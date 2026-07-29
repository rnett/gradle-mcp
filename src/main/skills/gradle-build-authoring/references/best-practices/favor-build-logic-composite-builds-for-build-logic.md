# Favor `build-logic` Composite Builds for Build Logic
You should set up a [Composite Build](https://docs.gradle.org/current/userguide/composite_builds.html#defining_composite_builds) (Use `gradle_docs(path="userguide/composite_builds.html#defining_composite_builds")`.) (often called an "included build") to hold your build logic---including any custom plugins, convention plugins, and other build-specific customizations.  

## Explanation
The preferred location for build logic is an included build (typically named `build-logic`), **not** in `buildSrc`.  
The automatically available `buildSrc` is great for rapid prototyping, but it comes with some subtle disadvantages:  
* There are classloader differences in how these 2 approaches behave that can be surprising; included builds are treated just like external dependencies, which is a simpler mental model. Dependency resolution behaves subtly differently in `buildSrc`.

* There can potentially be fewer task invalidations in a build when files in an included build are modified, leading to faster builds. Any change in `buildSrc` causes the entire build to become out-of-date, whereas changes in a subproject of an included build only cause projects in the build using the products of that particular subproject to be out-of-date.

* Included builds are complete Gradle builds and can be opened, worked on, and built independently as standalone projects. It is straightforward to publish their products, including plugins, in order to share them with other projects.

* The `buildSrc` project automatically applies the `java` plugin, which may be unnecessary.

One important caveat to this recommendation is when creating `Settings` plugins. Defining these in a `build-logic` project requires it to be included in the `pluginManagement` block of the main build's `settings.gradle(.kts)` file, in order to make these plugins available to the build early enough to be applied to the `Settings` instance. This is possible, but reduces Build Caching capability, potentially impacting performance. A better solution is to use a separate, minimal, included build (e.g. `build-logic-settings`) to hold only `Settings` plugins.  
Another potential reason to use `buildSrc` is if you have a very large number of subprojects within your included `build-logic`. Applying a different set of `build-logic` plugins to the subprojects in your *including* build will result in a different classpath being used for each. This may have performance implications and make your build harder to understand. Using different plugin combinations can cause features like [Build Services](https://docs.gradle.org/current/userguide/build_services.html#build_services) (Use `gradle_docs(path="userguide/build_services.html#build_services")`.) to break in difficult to diagnose ways.  
Ideally, there would be no difference between using `buildSrc` and an included build, as `buildSrc` is intended to behave like an implicitly available included build. However, due to historical reasons, these subtle differences still exist. As this changes, this recommendation may be revised in the future. For now, these differences can introduce confusion.  
Since setting up a composite build requires only minimal additional configuration, we recommend using it over `buildSrc` in most cases, especially for creating convention plugins.  

## Example
### Don't Do This
```kotlin
├── build.gradle.kts
├── buildSrc
│    ├── build.gradle.kts
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        ├── MyPlugin.java
│                        └── MyTask.java
└── settings.gradle.kts
```

```groovy
├── build.gradle
├── buildSrc
│    ├── build.gradle
│    └── src
│        └── main
│            └── java
│                └── org
│                    └── example
│                        ├── MyPlugin.java
│                        └── MyTask.java
└── settings.gradle
```

build.gradle.kts  

```kotlin
// This file is located in /buildSrc

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.myplugin"
            implementationClass = "org.example.MyPlugin"
        }
    }
}
```

build.gradle  

```groovy
// This file is located in /buildSrc

plugins {
    id "java-gradle-plugin"
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.myplugin"
            implementationClass = "org.example.MyPlugin"
        }
    }
}
```

**Set up a Plugin Build**: This is the same using either method.  
settings.gradle.kts  

```kotlin
rootProject.name = "favor-composite-builds"
```

settings.gradle  

```groovy
rootProject.name = "favor-composite-builds"
```

**`buildSrc` products are automatically usable**: There is no additional configuration with this method.  

### Do This Instead

```kotlin
├── build-logic
│    ├── plugin
│    │    ├── build.gradle.kts
│    │    └── src
│    │        └── main
│    │            └── java
│    │                └── org
│    │                    └── example
│    │                        ├── MyPlugin.java
│    │                        └── MyTask.java
│    └── settings.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

```groovy
├── build-logic
│    ├── plugin
│    │    ├── build.gradle
│    │    └── src
│    │        └── main
│    │            └── java
│    │                └── org
│    │                    └── example
│    │                        ├── MyPlugin.java
│    │                        └── MyTask.java
│    └── settings.gradle
├── build.gradle
└── settings.gradle
```

build.gradle.kts  

```kotlin
// This file is located in /build-logic/plugin

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.myplugin"
            implementationClass = "org.example.MyPlugin"
        }
    }
}
```

build.gradle  

```groovy
// This file is located in /build-logic/plugin

plugins {
    id "java-gradle-plugin"
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.example.myplugin"
            implementationClass = "org.example.MyPlugin"
        }
    }
}
```

**Set up a Plugin Build**: This is the same using either method.  
settings.gradle.kts  

```kotlin
// This file is located in the root project

includeBuild("build-logic") (1)

rootProject.name = "favor-composite-builds"
```

settings.gradle  

```groovy
// This file is located in the root project

includeBuild("build-logic") (1)

rootProject.name = "favor-composite-builds"
```

settings.gradle.kts  

```kotlin
// This file is located in /build-logic

rootProject.name = "build-logic"

include("plugin") (2)
```

settings.gradle  

```groovy
// This file is located in /build-logic

rootProject.name = "build-logic"

include("plugin") (2)
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Composite builds must be explicitly included** : Use the `includeBuild` method to locate and include a build in order to use its products. |
| **2** | **Structure your included build into subprojects**: This allows the main build to only depend on the necessary parts of the included build.  |

## References
* [Composite Builds in the Multi-Project Builds Tutorial](https://docs.gradle.org/current/userguide/part3_multi_project_builds.html#step_3_understand_composite_builds) (Use `gradle_docs(path="userguide/part3_multi_project_builds.html#step_3_understand_composite_builds")`.)

* [Composite Builds reference documentation](https://docs.gradle.org/current/userguide/composite_builds.html#composite_builds) (Use `gradle_docs(path="userguide/composite_builds.html#composite_builds")`.)

* [Gradle Issue #6045: buildSrc vs. included builds](https://github.com/gradle/gradle/issues/6045)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
