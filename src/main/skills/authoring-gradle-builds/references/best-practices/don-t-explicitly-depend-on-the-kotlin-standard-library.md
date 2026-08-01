<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: a7103d78c370f3677513bb52dd9dadd71f3000c803d6ba6e918cfc857868e272
-->
# Don't Explicitly Depend on the Kotlin Standard Library
The Kotlin Gradle Plugin automatically adds a dependency on the Kotlin standard library (`stdlib`) to each source set, so there is no need to declare it explicitly.  

## Explanation
The version of the standard library added is the same as the version of the Kotlin Gradle Plugin applied to the project. If your build does not require a specific or different version of the standard library, you should avoid adding it manually.  

|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | Setting the `kotlin.stdlib.default.dependency` property to `false` prevents the Kotlin plugin from automatically adding the Kotlin standard library dependency to your project. This can be useful in specific scenarios, such as when you want to manage the Kotlin standard library dependency version manually. |

## Example
### Don't Do This
build.gradle.kts  

```kotlin
plugins {
    kotlin("jvm").version("2.3.21")
}

dependencies {
    api(kotlin("stdlib")) (1)
}
```

build.gradle  

```groovy
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:2.3.21") (1)
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **`stdlib` is explicitly depended upon**: This project contains an implicit dependency on the Kotlin standard library, which is required to compile its source code. |

### Do This Instead
build.gradle.kts  

```kotlin
plugins {
    kotlin("jvm").version("2.3.21") (1)
}
```

build.gradle  

```groovy
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.21"  (1)
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **`stdlib` dependency is not included explicitly**: The standard library remains available for use, and source code requiring it can be compiled without any issues. |

## References
* [the `kotlin()` function (Use `gradle_docs(path="kotlin-dsl/gradle/org.gradle.kotlin.dsl/kotlin.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
