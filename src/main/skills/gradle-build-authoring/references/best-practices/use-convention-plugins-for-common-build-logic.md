# Use Convention Plugins for Common Build Logic
Use [convention plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_precompiled.html#implementing_precompiled_plugins) (Use `gradle_docs(path="userguide/implementing_gradle_plugins_precompiled.html#implementing_precompiled_plugins")`.) to encapsulate and reuse shared build logic across multiple projects in your build.  

## Explanation
Instead of duplicating configuration across multiple build scripts, you can easily move common logic into a reusable convention plugins.  
This approach offers several benefits:  
* **Reduces duplication**: Shared build logic lives in one place, making your build easier to understand.

* **Unlocks modularization**: Convention plugins can apply other convention plugins, allowing you to orchestrate your build logic from small pieces.

* **Centralizes configuration**: Updates to build behavior can be made in one file instead of many.

* **Keeps build files clean**: Project build files stay focused on project-specific configuration.

* **Improves IDE support**: IDEs can better understand and validate build logic when it is structured in plugins.

Convention plugins are quicker to create than [typed binary plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_binary.html#implementing_binary_plugins) (Use `gradle_docs(path="userguide/implementing_gradle_plugins_binary.html#implementing_binary_plugins")`.) extending the `Plugin` class. They are often a better choice for build logic that does not need to be shared outside a build, and that is simple enough to not require additional type safeness and testability benefits. Unlike binary plugins, convention plugins allow accessing plugin extensions, tasks and configurations via static accessors in build scripts written in Kotlin.  
While setting up convention plugins takes some initial effort, it pays off by simplifying maintenance, improving comprehensibility, and making it easier to add new projects as your codebase grows.  
As mentioned in Favor `build-logic` Composite Builds for Build Logic, we recommend placing your convention plugins in an included build (often named `build-logic`) instead of `buildSrc`.  

## Example
### Don't Do This
project-a/build.gradle.kts  

```kotlin
plugins {
    `java-library`
}

// Duplicated configuration across multiple build files
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation")) (1)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1 (2)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") (3)
}
```

project-b/build.gradle.kts  

```kotlin
plugins {
    `java-library`
}

// Duplicated configuration across multiple build files
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation")) (1)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1 (2)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") (3)
    api("com.google.guava:guava:23.0") (4)
}
```

project-a/build.gradle  

```groovy
plugins {
    id("java-library")
}

// Duplicated configuration across multiple build files
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs += ["-Xlint:unchecked", "-Xlint:deprecation"] (1)
}

test {
    useJUnitPlatform()
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1 (2)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") (3)
}
```

project-b/build.gradle  

```groovy
plugins {
    id("java-library")
}

// Duplicated configuration across multiple build files
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs += ["-Xlint:unchecked", "-Xlint:deprecation"] (1)
}

test {
    useJUnitPlatform()
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1 (2)
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3") (3)
    api("com.google.guava:guava:23.0") (4)
}
```

|-------|-----------------------------------------------------------------------|
| **1** | Common compiler settings repeated across multiple projects.           |
| **2** | Shared test configuration that must be maintained in multiple places. |
| **3** | Common dependencies that could be managed centrally.                  |
| **4** | Unique project dependencies.                                          |

### Do This Instead
Create included build containing convention plugins for your build in `build-logic` and add it to your settings file:  
settings.gradle.kts  

```kotlin
pluginManagement {
    includeBuild("build-logic") (1)
}
```

build-logic/build.gradle.kts  

```kotlin
plugins {
    `kotlin-dsl` (2)
}
```

settings.gradle  

```groovy
pluginManagement {
    includeBuild("build-logic") (1)
}
```

build-logic/build.gradle  

```groovy
plugins {
    id("groovy-gradle-plugin") (2)
}
```

|-------|--------------------------------------------------------------------|
| **1** | Include the `build-logic` build, which defines convention plugins. |
| **2** | Enable the use of Kotlin DSL in `build-logic`.                     |

Create convention plugins for each type of project in `build-logic`:  
build-logic/src/main/kotlin/my.base-java-library.gradle.kts  

```kotlin
plugins {
    `java-library`
}

java { (1)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}
```

build-logic/src/main/kotlin/my.java-library.gradle.kts  

```kotlin
plugins { (2)
    id("my.base-java-library")
    id("my.java-use-junit5")
}
```

build-logic/src/main/kotlin/my.java-use-junit5.gradle.kts  

```kotlin
plugins {
    `java-library`
}

tasks.withType<Test>().configureEach { (3)
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
}
```

build-logic/src/main/groovy/my.base-java-library.gradle  

```groovy
plugins {
    id("java-library")
}

java { (1)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs += ["-Xlint:unchecked", "-Xlint:deprecation"]
}
```

build-logic/src/main/groovy/my.java-library.gradle  

```groovy
plugins { (2)
    id("my.base-java-library")
    id("my.java-use-junit5")
}
```

build-logic/src/main/groovy/my.java-use-junit5.gradle  

```groovy
plugins {
    id("java-library")
}

tasks.withType(Test).configureEach { (3)
    useJUnitPlatform()
    maxParallelForks = Runtime.runtime.availableProcessors().intdiv(2) ?: 1
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
}
```

|-------|---------------------------------------------------------|
| **1** | Default settings for a Java library plugin.             |
| **2** | A convention plugin can apply other convention plugins. |
| **3** | JUnit 5 configuration moved to a convention plugin.     |

And apply these plugin in any build files to use the shared logic:  
project-a/build.gradle.kts  

```kotlin
plugins {
    id("my.java-library") (6)
}
```

project-b/build.gradle.kts  

```kotlin
plugins {
    id("my.java-library") (6)
}

dependencies {
    api("com.google.guava:guava:23.0") (7)
}
```

project-a/build.gradle  

```groovy
plugins {
    id("my.java-library") (6)
}
```

project-b/build.gradle  

```groovy
plugins {
    id("my.java-library") (6)
}

dependencies {
    api("com.google.guava:guava:23.0") (7)
}
```

## References
* [Developing Custom Gradle Plugins](https://docs.gradle.org/current/userguide/plugins/custom_plugins.html#custom_plugins) (Use `gradle_docs(path="userguide/plugins/custom_plugins.html#custom_plugins")`.)

* [Implementing Pre-compiled Script Plugins](https://docs.gradle.org/current/userguide/implementing_gradle_plugins_precompiled.html#implementing_precompiled_plugins) (Use `gradle_docs(path="userguide/implementing_gradle_plugins_precompiled.html#implementing_precompiled_plugins")`.)

* [Types of Plugins](https://docs.gradle.org/current/userguide/plugin-development/plugins.html#types_of_plugins) (Use `gradle_docs(path="userguide/plugin-development/plugins.html#types_of_plugins")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
