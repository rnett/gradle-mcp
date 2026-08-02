# Apply Plugins Using the `plugins` Block
You should always use the `plugins` block to [apply plugins (Use `gradle_docs(path="userguide/plugin_basics.md")`.) in your build scripts.  

## Explanation
The `plugins` block is the preferred way to apply plugins in Gradle. The plugins API allows Gradle to better manage the loading of plugins and it is both more concise and less error-prone than adding dependencies to the buildscript's classpath explicitly in order to use the `apply` method.  
It allows Gradle to optimize the loading and reuse of plugin classes and helps inform tools about the potential properties and values in extensions the plugins will add to the build script. It is constrained to be idempotent (produce the same result every time) and side effect-free (safe for Gradle to execute at any time).  

## Example
### Don't Do This
build.gradle.kts  

```kotlin
buildscript {
    repositories {
        gradlePluginPortal() (1)
    }

    dependencies {
        classpath("com.google.protobuf:com.google.protobuf.gradle.plugin:0.9.4") (2)
    }
}

apply(plugin = "java") (3)
apply(plugin = "com.google.protobuf") (4)
```

build.gradle  

```groovy
buildscript {
    repositories {
        gradlePluginPortal() (1)
    }

    dependencies {
        classpath("com.google.protobuf:com.google.protobuf.gradle.plugin:0.9.4") (2)
    }
}

apply plugin: "java" (3)
apply plugin: "com.google.protobuf" (4)
```

|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | **Declare a Repository**: To use the legacy plugin application syntax, you need to explicitly tell Gradle where to find a plugin.                                             |
| **2** | **Declare a Plugin Dependency**: To use the legacy plugin application syntax with third-party plugins, you need to explicitly tell Gradle the full coordinates of the plugin. |
| **3** | **Apply a Core Plugin**: This is very similar using either method.                                                                                                            |
| **4** | **Apply a Third-Party Plugin**: The syntax is the same as for core Gradle plugins, but the version is not present at the point of application in your buildscript.            |

### Do This Instead
build.gradle.kts  

```kotlin
plugins {
    id("java") (1)
    id("com.google.protobuf").version("0.9.4") (2)
}
```

build.gradle  

```groovy
plugins {
    id("java") (1)
    id("com.google.protobuf").version("0.9.4") (2)
}
```

|-------|---------------------------------------------------------------------------------------------------------------|
| **1** | **Apply a Core Plugin**: This is very similar using either method.                                            |
| **2** | **Apply a Third-Party Plugin** : You specify the version using method chaining in the `plugins` block itself. |

## References
* [Using Plugins (Use `gradle_docs(path="userguide/plugins_intermediate.md")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
