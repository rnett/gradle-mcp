<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: f59bcf757ba20bdd23a22d96822fc668956531a436b12d8b75cd775b1b0d0f1b
-->
# Avoid `afterEvaluate`
Do not use [`project.afterEvaluate {}` (Use `gradle_docs(path="javadoc/org/gradle/api/Project.md")`.)) to configure tasks, wire properties, or react to plugin application. Use [lazy properties (Use `gradle_docs(path="userguide/properties_providers.md")`.) and [`pluginManager.withPlugin()` (Use `gradle_docs(path="javadoc/org/gradle/api/plugins/PluginManager.md")`.)) instead.  

## Explanation
`afterEvaluate` registers a callback that runs after Gradle finishes evaluating and configuring a project. It was historically used to "delay" reading a value until configuration was complete --- for example, reading an extension property that users set at the bottom of their build script, or checking whether another plugin was applied.  
This pattern is outdated, and problematic for several reasons:  
* **Ordering is fragile.** Multiple `afterEvaluate` callbacks execute in registration order. If two plugins or scripts both use `afterEvaluate`, one may see stale or incomplete configuration depending on which was registered first. This creates subtle bugs that are extremely difficult to diagnose.

* **It defeats task configuration avoidance.** Tasks registered or configured inside `afterEvaluate` are touched eagerly during configuration, even if they will never execute. This may cause unnecessary work and slow down the configuration phase of the build.

* **It is incompatible with the [Configuration Cache (Use `gradle_docs(path="userguide/configuration_cache.md")`.).** `afterEvaluate` callbacks capture mutable project state that cannot be serialized reliably.

Gradle's lazy `Property` and `Provider` types solve the same underlying problem --- deferring value resolution --- without any of these drawbacks. A `Property<T>` can be wired at configuration time but its value is resolved only when needed, typically during task execution. This makes configuration order-independent and fully compatible with the configuration cache.  
Similarly, `pluginManager.withPlugin()` reacts to plugin application safely and immediately, regardless of when the plugin is actually applied --- no callback ordering to worry about.  

### When `afterEvaluate` may still be appropriate
There are narrow use cases where `afterEvaluate` remains the only available hook:  
* **Fail-fast validation** --- verifying that required project configuration has been set and failing the build early with a clear error message.

* **Logging or reporting** --- printing diagnostic information about the project's final configuration state.

Even in these cases, exercise caution: your `afterEvaluate` must be the last (or only) one registered to see the final configuration state. If another plugin registers an `afterEvaluate` after yours, your callback may see incomplete configuration.  
If you find yourself reaching for `afterEvaluate` because Gradle's lazy APIs do not cover your use case, consider [filing a bug](https://github.com/gradle/gradle/issues). `afterEvaluate` should be a last resort, not a first choice.  

## Example
Given the following project layout:  

```kotlin
.
├── build.gradle.kts
├── buildSrc/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── AppInfoPlugin.kt
└── settings.gradle.kts
```

```groovy
.
├── build.gradle
├── buildSrc/
│   ├── build.gradle
│   └── src/main/groovy/
│       └── AppInfoPlugin.groovy
└── settings.gradle
```

### Don't Do This
The plugin uses `afterEvaluate` to delay reading the extension value and to check whether `java-library` was applied:  
buildSrc/src/main/kotlin/AppInfoPlugin.kt  

```kotlin
interface AppInfoExtension {
    val appName: Property<String>
}

class AppInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("appInfo", AppInfoExtension::class.java)

        project.afterEvaluate { (1)
            val name = extension.appName.getOrElse("unnamed") (2)

            tasks.register("printAppInfo") { (3)
                doLast {
                    println("App: $name")
                }
            }

            if (plugins.hasPlugin("java-library")) { (4)
                tasks.named("printAppInfo") {
                    doLast {
                        println("Jar: $name.jar")
                    }
                }
                tasks.named("jar", Jar::class.java) {
                    archiveBaseName.set(name)
                }
            }
        }
    }
}
```

buildSrc/src/main/groovy/AppInfoPlugin.groovy  

```groovy
interface AppInfoExtension {
    Property<String> getAppName()
}

class AppInfoPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("appInfo", AppInfoExtension)

        project.afterEvaluate { (1)
            def name = extension.appName.getOrElse("unnamed") (2)

            project.tasks.register("printAppInfo") { (3)
                doLast {
                    println "App: $name"
                }
            }

            if (project.plugins.hasPlugin("java-library")) { (4)
                project.tasks.named("printAppInfo") {
                    doLast {
                        println "Jar: ${name}.jar"
                    }
                }
                project.tasks.named("jar", Jar) {
                    archiveBaseName.set(name)
                }
            }
        }
    }
}
```

|-------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | The plugin's `afterEvaluate` runs before any `afterEvaluate` registered later in the build script --- ordering depends on registration order.     |
| **2** | `getOrElse` reads the property's current value immediately. If the value is set in a later `afterEvaluate`, this will never see it.               |
| **3** | Registering a task inside `afterEvaluate` defeats [task configuration avoidance (Use `gradle_docs(path="userguide/task_configuration_avoidance.md")`.). |
| **4** | Checking plugin presence inside `afterEvaluate` assumes all plugins have been applied before this callback runs.                                  |

The build script applies the plugin and sets the extension value in its own `afterEvaluate`:  
build.gradle.kts  

```kotlin
plugins {
    id("java-library")
    id("app-info-plugin")
}

afterEvaluate {
    the<AppInfoExtension>().appName.set("my-app") (1)
}
```

build.gradle  

```groovy
plugins {
    id 'java-library'
    id 'app-info-plugin'
}

afterEvaluate {
    appInfo { (1)
        appName.set('my-app')
    }
}
```

|-------|-----------------------------------------------------------------------------------------------------------------------------------|
| **1** | This `afterEvaluate` runs after the plugin's --- by the time it sets the name, the plugin has already captured the default value. |

Running `printAppInfo` outputs `unnamed` --- **not** `my-app` as the user intended:  

```text
> Task :printAppInfo
App: unnamed
Jar: unnamed.jar

BUILD SUCCESSFUL in 0s
5 actionable tasks: 5 executed
```

The plugin's `afterEvaluate` callback was registered first (during `Plugin.apply()`) and ran first, reading the property before the build script's `afterEvaluate` had a chance to set it.  

### Do This Instead
The proper way to write this plugin uses lazy `Property` types and `pluginManager.withPlugin()`:  
buildSrc/src/main/kotlin/AppInfoPlugin.kt  

```kotlin
interface AppInfoExtension {
    val appName: Property<String>
}

class AppInfoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("appInfo", AppInfoExtension::class.java)
        extension.appName.convention("unnamed") (1)

        project.tasks.register("printAppInfo") {
            val name = extension.appName
            doLast {
                println("App: ${name.get()}") (2)
            }
        }

        project.pluginManager.withPlugin("java-library") { (3)
            project.tasks.named("printAppInfo") {
                val jarName = extension.appName
                doLast {
                    println("Jar: ${jarName.get()}.jar")
                }
            }
            project.tasks.named("jar", Jar::class.java) {
                archiveBaseName.set(extension.appName)
            }
        }
    }
}
```

buildSrc/src/main/groovy/AppInfoPlugin.groovy  

```groovy
interface AppInfoExtension {
    Property<String> getAppName()
}

class AppInfoPlugin implements Plugin<Project> {
    void apply(Project project) {
        def extension = project.extensions.create("appInfo", AppInfoExtension)
        extension.appName.convention("unnamed") (1)

        project.tasks.register("printAppInfo") {
            def name = extension.appName
            doLast {
                println "App: ${name.get()}" (2)
            }
        }

        project.pluginManager.withPlugin("java-library") { (3)
            project.tasks.named("printAppInfo") {
                def jarName = extension.appName
                doLast {
                    println "Jar: ${jarName.get()}.jar"
                }
            }
            project.tasks.named("jar", Jar) {
                archiveBaseName.set(extension.appName)
            }
        }
    }
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| **1** | `convention()` provides a default value that is used only if no explicit value is set via `set()`.                                                 |
| **2** | The value is resolved at execution time via `get()` --- configuration order does not matter.                                                       |
| **3** | `pluginManager.withPlugin()` fires when the plugin is applied, regardless of order. If the plugin is never applied, the callback is never invoked. |

The build script is nearly identical --- the change is in the plugin, not the consumer:  
build.gradle.kts  

```kotlin
plugins {
    id("java-library")
    id("app-info-plugin")
}

appInfo {
    appName.set("my-app") (1)
}
```

build.gradle  

```groovy
plugins {
    id 'java-library'
    id 'app-info-plugin'
}

appInfo {
    appName.set('my-app') (1)
}
```

|-------|----------------------------------------------------------------------------------------------------------------------------------|
| **1** | The value is set during normal configuration. Because the plugin wires the `Property` lazily, it is only read at execution time. |

Running `printAppInfo` now correctly outputs `my-app`:  

```text
$ gradlew printAppInfo
> Task :printAppInfo
App: my-app
Jar: my-app.jar

BUILD SUCCESSFUL in 0s
5 actionable tasks: 5 executed
```

## References
* [Properties and Providers (Use `gradle_docs(path="userguide/properties_providers.md")`.)

* [Lazy Configuration (Use `gradle_docs(path="userguide/lazy_configuration.md")`.)

* [Task Configuration Avoidance (Use `gradle_docs(path="userguide/task_configuration_avoidance.md")`.)

* Don't Assume your Plugin is Applied after Another

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
