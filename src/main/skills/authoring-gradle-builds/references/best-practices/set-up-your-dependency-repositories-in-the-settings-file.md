<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: 5975498f3e64716ea4e569d33577f713267e87fbab94c6bb61e10ecad1abcb18
-->
# Set up your Dependency Repositories in the Settings file
Declare your repositories for your plugins and dependencies in `settings.gradle.kts`.  

## Explanation
Using `settings.gradle.kts` file to declare repositories has several benefits:  
* **Avoids repetition** : Centralizing repository declarations eliminates the need to repeat them in each project's `build.gradle.kts`.

* **Improves debuggability**: Ensures all projects resolve dependencies during resolution from the same repositories, in a consistent order.

* **Matches the build model**: Repositories are not part of the project definition; they are part of global build logic, so settings is a more appropriate place for them.

|---|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|   | While [`dependencyResolutionManagement.repositories` is an incubating API](https://github.com/gradle/gradle/issues/32443), it is the preferred way of declaring repositories. |

## Example
### Don't Do This
You could set up repositories in individual `build.gradle.kts` files with:  
build.gradle.kts  

```kotlin
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("java")
}

repositories {
    mavenCentral()
}
```

build.gradle  

```groovy
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("java")
}

repositories {
    mavenCentral()
}
```

### Do This Instead
Instead, you should set them up in `settings.gradle.kts` like this:  
settings.gradle.kts  

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
```

settings.gradle  

```groovy
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
```

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
