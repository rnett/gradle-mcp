<!--
class: generated
generator: best-practices
gradle-version: 9.6.1
hash: cdb379549b9ce829e243c0bedbf4e7cb460362152e73cee517bfae11c1108f00
-->
# Don't Assume your Plugin is Applied after Another
Gradle's plugin application is deterministic but opaque. It is difficult to reason about, especially across multiple build scripts, projects, convention plugins, or included builds.  
As a result, you should not write build logic or plugins that depend on a specific plugin application order.  

## Explanation
In a single `build.gradle(.kts)` file, plugins appear to be applied sequentially:  
build.gradle.kts  

```kotlin
plugins {
    id("pluginA")
    id("pluginB")
}
```

build.gradle  

```groovy
plugins {
    id("pluginA")
    id("pluginB")
}
```

However, in [multi-project builds](https://docs.gradle.org/current/userguide/multi_project_builds.html#multi_project_builds) (Use `gradle_docs(path="userguide/multi_project_builds.html#multi_project_builds")`.), with multiple `build.gradle(.kts)` files or a [convention plugin](https://docs.gradle.org/current/userguide/plugins.html#sec:convention_plugins) (Use `gradle_docs(path="userguide/plugins.html#sec:convention_plugins")`.), plugin application can be hard to determine:  
app/build.gradle.kts  

```kotlin
plugins {
    id("pluginA")
    id("pluginB")
}
```

lib/build.gradle.kts  

```kotlin
plugins {
    id("pluginC")
    id("pluginD")
}
```

app/build.gradle  

```groovy
plugins {
    id("pluginA")
    id("pluginB")
}
```

lib/build.gradle  

```groovy
plugins {
    id("pluginC")
    id("pluginD")
}
```

You cannot assume whether `pluginA`, `pluginB`, `pluginC`, or `pluginD` will be applied first because `pluginA` could apply `pluginD`.  

### Build Engineers
Writing build logic that assumes plugin ordering can lead to brittle behavior and fragile builds that break when project structure changes.  
Don't rely on blocks like `allprojects {}`, `subprojects {}`, or `afterEvaluate {}` that are highly dependent on project structure and file layout. They can be difficult to decipher and may depend on configuration details that are hard to understand completely without running the build.  
Avoid build logic that assumes ordering between different projects, included builds, or applied scripts. While the application order is deterministic, minor structural changes (such as adding a new project or renaming an include) can easily result in an unexpected change to that plugin application order.  

### Plugin Developers
Users should be able to apply your plugin in either order and have it behave correctly:  
build.gradle.kts  

```kotlin
plugins {
  id("my-plugin")
  id("plugin-i-depend-on")
}
```

build.gradle  

```groovy
plugins {
  id("my-plugin")
  id("plugin-i-depend-on")
}
```

or  
build.gradle.kts  

```kotlin
plugins {
  id("plugin-i-depend-on")
  id("my-plugin")
}
```

build.gradle  

```groovy
plugins {
  id("plugin-i-depend-on")
  id("my-plugin")
}
```

If your plugin only works in one of these cases, it's relying on plugin order and will be fragile in real builds.  
If your plugin cannot function without another plugin, apply it explicitly at the start of `Plugin.apply`:  
MyPlugin.kt  

```kotlin
// Ensure required plugin is applied
project.pluginManager.apply("com.example.required-plugin")
```

MyPlugin.groovy  

```groovy
// Ensure required plugin is applied
project.pluginManager.apply('com.example.required-plugin')
```

If your plugin only needs to integrate with another plugin when it's present, react to its application using [`pluginManager.withPlugin()`](https://docs.gradle.org/current/javadoc/org/gradle/api/plugins/PluginManager.html#withPlugin(java.lang.String,org.gradle.api.Action) (Use `gradle_docs(path="javadoc/org/gradle/api/plugins/PluginManager.html#withPlugin(java.lang.String,org.gradle.api.Action")`.)) or [`plugins.configureEach {}`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#configureEach(org.gradle.api.Action) (Use `gradle_docs(path="javadoc/org/gradle/api/DomainObjectCollection.html#configureEach(org.gradle.api.Action")`.)):  
MyPlugin.kt  

```kotlin
// Configure behavior that depends on required-plugin using the plugin id (preferred)
project.pluginManager.withPlugin("com.example.required-plugin") {  }

// Configure behavior that depends on RequiredPlugin using the plugin class (if no id is available)
project.plugins.configureEach { plugin ->
    when (plugin) { is com.example.RequiredPlugin -> {  } }
}
```

MyPlugin.groovy  

```groovy
// Configure behavior that depends on required-plugin using the plugin id (preferred)
project.pluginManager.withPlugin('com.example.required-plugin') {  }

// Configure behavior that depends on RequiredPlugin using the plugin class (if no id is available)
project.plugins.configureEach { plugin ->
    if (plugin instanceof com.example.RequiredPlugin) {  }
}
```

This is order-independent and safe.  

## Example
Given the following project layout:  

```kotlin
.
├── app/
│   └── build.gradle.kts
├── buildSrc/
│   ├── build.gradle.kts
│   └── src/main/kotlin/MyPlugin.kt
├── settings.gradle.kts
└── build.gradle.kts
```

```groovy
.
├── app/
│   └── build.gradle
├── buildSrc/
│   ├── build.gradle
│   └── src/main/groovy/MyPlugin.groovy
├── settings.gradle
└── build.gradle
```

### Don't Do This
This setup makes several assumptions about the `java` plugin.  
In the root build, `subprojects {}` and `afterEvaluate {}` obscure when a plugin is applied and attempt to force ordering:  
build.gradle.kts  

```kotlin
subprojects {
    // Apply the Java plugin to every subproject
    afterEvaluate {
        // This runs after the app subproject's build script is evaluated and results in an error
        pluginManager.apply("java")
    }
}
```

build.gradle  

```groovy
subprojects {
    // Apply the Java plugin to every subproject
    afterEvaluate {
        // This runs after the app subproject's build script is evaluated and results in an error
        apply plugin: 'java'
    }
}
```

In the `app` subproject, the build file uses `extensions.getByType(...​)` which assumes `java` has already been applied:  
app/build.gradle.kts  

```kotlin
plugins {
    id("myplugin")
}
// Assumes 'java' plugin is present
extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>().apply {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
```

app/build.gradle  

```groovy
plugins {
    id 'myplugin'
}
// Assumes 'java' plugin is present
project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension).with {
    toolchain.languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(21))
}
```

In the plugin implementation, `MyPlugin.kt` or `MyPlugin.groovy` also assumes `java` is already applied:  
buildSrc/src/main/kotlin/MyPlugin.kt  

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Assumes 'java' plugin is present
        // WARNING: This will fail if the 'java' plugin hasn't been applied yet.
        project.extensions.getByType(JavaPluginExtension::class.java).toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
```

buildSrc/src/main/groovy/MyPlugin.groovy  

```groovy
class MyPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Assumes 'java' plugin is present
        // WARNING: This will fail if the 'java' plugin hasn't been applied yet.
        project.extensions.configure(JavaPluginExtension) {
            it.toolchain {
                it.languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
```

### Do This Instead
The fixed version removes `afterEvaluate` and avoids assumptions about when or where the `java` plugin is applied.  
The root build file has been deleted as there is no longer a need to use `subprojects {}`.  
In the `app` subproject, the build file uses `plugins.withPlugin("java") {}` to safely configure tasks once `java` is applied:  
app/build.gradle.kts  

```kotlin
pluginManager.withPlugin("java") {
    extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

app/build.gradle  

```groovy
project.pluginManager.withPlugin('java') {
    project.extensions.configure(org.gradle.api.plugins.JavaPluginExtension) {
        it.toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
```

In the plugin implementation, `MyPlugin.kt` or `MyPlugin.groovy` explicitly applies the `java` plugin:  
buildSrc/src/main/kotlin/MyPlugin.kt  

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // If your plugin requires 'java', apply it so order doesn't matter
        project.pluginManager.apply("java")
        // Now it's safe to configure Java things immediately
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
```

buildSrc/src/main/groovy/MyPlugin.groovy  

```groovy
class MyPlugin implements Plugin<Project> {
    void apply(Project project) {
        // If your plugin requires 'java', apply it so order doesn't matter
        project.pluginManager.apply('java')
        // Now it's safe to configure Java things immediately
        project.extensions.configure(JavaPluginExtension) {
            it.toolchain {
                it.languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
}
```

## References
* [Using Plugins](https://docs.gradle.org/current/userguide/plugins_intermediate.html#sec:using_plugins) (Use `gradle_docs(path="userguide/plugins_intermediate.html#sec:using_plugins")`.)

---

For the most up-to-date guidance, use `gradle_docs` with `tag:best-practices`.
