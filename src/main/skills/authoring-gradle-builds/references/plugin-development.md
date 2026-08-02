<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Plugin Development

Produce distributable plugins from a standalone build. Prefer precompiled script plugins in `build-logic` for in-repo sharing (see [Convention Plugins](convention-plugins.md)); this reference is for BINARY plugins that are packaged and consumed across different projects or repositories.

## `java-gradle-plugin` and `gradlePlugin {}`

Apply the `java-gradle-plugin` to manage the boilerplate of plugin production. Use the `gradlePlugin {}` block to register implementations, their globally-unique plugin IDs, and the classes that implement the `plugin` interface.

```kotlin
plugins {
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins {
        create("myProjectCustomPlugin") {
            id = "com.example.my-project-custom"
            implementationClass = "com.example.MyCustomPlugin"
        }
    }
}
```

**Default:** Use stable, globally-unique IDs and implement the `Plugin<Project>` interface. Rely on the automatic generation of plugin descriptors and marker artifacts to ensure consumers can resolve the plugin based on its ID.

**Anti-pattern:** Manually author plugin descriptors or marker JARs, use non-unique IDs that might collide with community plugins, or apply the plugin via `apply(plugin = "...")` in modern builds instead of the `plugins {}` block.

## Plugin API dependencies and diagnostics

In plugin projects, use `compileOnlyApi` for `gradleApi()` on Gradle 9.4 and later so the Gradle API is available to plugin consumers at compile time without being bundled on the plugin runtime classpath. Report actionable plugin diagnostics through the Problems API, including a stable problem ID, details, severity, solution, and documentation link; throw only when continuing is unsafe.

**Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying the Gradle 9.4+ rule.

## Extensions and Task Validation

Expose a public extension API to allow consumers to configure your plugin's behavior. Use managed properties for the extension and validate task properties to ensure the plugin fails fast with clear error messages.

```kotlin
interface MyPluginExtension {
    val message: Property<String>
}

class MyCustomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<MyPluginExtension>("myPlugin")
        
        project.tasks.register<MyMessageTask>("printMessage") {
            message.set(extension.message)
        }
    }
}
```

**Default:** Keep the public API minimal and stable. Use managed types (`Property`, `Provider`) for all extension fields and task inputs to maintain lazy wiring. Avoid leaking internal implementation types into the public extension.

**Anti-pattern:** Use mutable state (e.g., `var`) in extensions, resolve providers during the `apply` method, or leak internal `Project` logic into the extension's public methods.

## Plugin Classpaths and Variants

Manage the implementation classpath carefully to avoid dependency leakage. Use Gradle Plugin API version attributes to ensure compatible variant matching across different Gradle versions.

**Default:** Test the plugin against the lowest supported Gradle version required by your consumers. Keep implementation dependencies narrow to minimize the risk of classpath conflicts in the consumer's build.

**Anti-pattern:** Force a specific Gradle version on the consumer, bundle dependencies that should be provided by the Gradle runtime, or ignore the Plugin API version attributes.

## Functional Testing with TestKit

Use `gradleTestKit()` to perform end-to-end functional testing. `GradleRunner` allows you to execute actual build scripts against a temporary consumer project and assert the outcome of tasks.

```kotlin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MyPluginTest {
    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `test plugin behavior`() {
        // Setup temporary project files
        File(testProjectDir, "settings.gradle.kts").writeText("rootProject.name = \"test-plugin\"")
        File(testProjectDir, "build.gradle.kts").writeText("""
            plugins {
                id("com.example.my-project-custom")
            }
            
            myPlugin {
                message.set("Hello from a lazy task")
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath() // MUST be present for the plugin under test
            .withArguments("printMessage")
            .build()

        assert(result.task(":printMessage")?.outcome == TaskOutcome.SUCCESS)
        assert(result.output.contains("Hello from a lazy task"))
    }
}
```

**Default:** Use a version matrix to test your plugin across a range of supported Gradle versions. Ensure the plugin under test is explicitly on the TestKit classpath.

**Anti-pattern:** Use unit tests to mock the `Project` or `Task` objects (which often leads to "success" in tests that fail in real builds), avoid temporary project directories, or omit task-outcome assertions.

See [Testing Configuration](testing-configuration.md) for consumer-side test setup; this section focuses strictly on the plugin author's functional verification.

## Init and precompiled script plugin boundaries

Use init plugins only for truly global machine, enterprise, or invocation-wide policy. In precompiled plugin `plugins {}` blocks, do not use `version(...)` or `apply false`; put external plugin versions on the precompiled build's implementation classpath instead. Do not specify versions in precompiled Settings plugins.

## Publishing Plugins

Use `com.gradle.plugin-publish` to distribute your plugin to the Gradle Plugin Portal or a private repository. Ensure you use a signing key and keep credentials out of version control.

```kotlin
plugins {
    `com.gradle.plugin-publish`
}

gradlePluginPortal {
    // Portal-specific metadata
}
```

**Default:** Always run `publishPlugins --validate-only` before attempting a real release to verify marker requirements and metadata. Use a dedicated signing server or environment variable for GPG keys.

**Anti-pattern:** Commit `gradle.properties` containing portal credentials, skip signing for public releases, or publish to the Portal without first validating the marker artifacts.

See [Artifact Publishing](artifact-publishing.md) for general Maven artifact publishing; plugin publishing involves additional marker requirements for ID resolution.

## Plugin ID Governance

Prevent collisions and ensure predictable application by following strict ID naming conventions. Favor `apply-by-id` via the `plugins {}` block over `apply-by-type` to avoid premature class-loading.

**Default:** Use reverse-DNS notation for plugin IDs (e.g., `com.example.my-plugin`). Use the `plugins {}` block for application to enable Gradle's optimized plugin resolution and class-loading.

**Anti-pattern:** Use overly generic IDs (e.g., `java-helper`), mix ID-based and type-based application in the same project, or rely on manual marker resolution in private repositories.

## Config-Cache and Isolation Constraints

Plugin code must be compatible with the configuration cache and project isolation. Avoid capturing the `Project` object in task actions or resolving configurations during the configuration phase.

**Default:** Model all task inputs using managed properties and avoid accessing the live `Project` model inside `@TaskAction`. Use `BuildService` for shared state.

**Anti-pattern:** Store a reference to `Project` in a task field, resolve a dependency configuration in a plugin's `apply` method, or perform undeclared reads of the file system during configuration.

See [Advanced Configuration](advanced-configuration.md) and [Modules and Settings](modules-and-settings.md) for detailed isolation/cache constraints.

### Version notes

- **Gradle 9.x:** Use the latest `com.gradle.plugin-publish` for automated marker and metadata handling. Configuration cache and project isolation are the primary constraints for new plugin development.
- **Gradle 8.x:** `java-gradle-plugin` and TestKit are stable. plugin-publish 1.0.0+ auto-applies `java-gradle-plugin` and `maven-publish`.
- **Gradle 7.x:** Older versions of the publishing plugin may require manual application of `maven-publish` and `java-gradle-plugin` to generate markers.

**More info:**

- Implementing plugins: `gradle_docs` `tag:userguide`, path `userguide/implementing_gradle_plugins.md`
- Java Gradle Plugin: `gradle_docs` `tag:userguide`, path `userguide/java_gradle_plugin.md`
- Testing plugins: `gradle_docs` `tag:userguide`, path `userguide/testing_gradle_plugins.md`
- Publishing plugins: `gradle_docs` `tag:userguide`, path `userguide/publishing_gradle_plugins.md`
- Advanced plugin concepts: `gradle_docs` `tag:userguide`, path `userguide/plugin_introduction_advanced.md`
- Gradle documentation lookup: `gradle_docs`
