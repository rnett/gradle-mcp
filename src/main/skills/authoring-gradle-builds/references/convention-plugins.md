<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Convention Plugins

Put shared build policy in a `build-logic` included build and expose it through precompiled script plugins. Apply conventions explicitly in consuming projects. Keep plugin reactions event-driven, because sibling convention-plugin ordering is not a safe contract. Classify applied plugins as core, community/Portal, or custom/local before authoring their configuration.

## `build-logic` Composite Build

Use an included build for project-specific plugins, convention plugins, and reusable build logic. Keep its settings independent and make plugin implementation dependencies explicit.

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
}
```

```kotlin
// build-logic/settings.gradle.kts
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
// build-logic/build.gradle.kts
plugins {
    `kotlin-dsl`
}
```

**Default:** Keep build logic in the composite build, apply a named convention plugin from each consumer, and use the consuming build's version catalog or explicit implementation dependencies according to the existing repository pattern.

**Anti-pattern:** Copy the same configuration into every `build.gradle.kts`, publish private conventions merely to share them inside one repository, or hide shared behavior in root-wide project mutation.

See the frozen corpus entries [Favor `build-logic` composite builds for build logic](best-practices/favor-build-logic-composite-builds-for-build-logic.md) and [Use convention plugins for common build logic](best-practices/use-convention-plugins-for-common-build-logic.md) for the approved rationale.

## Precompiled Script Plugins

Name a `.gradle.kts` file under `build-logic/src/main/kotlin/` as the plugin ID. The script can apply plugins and configure their public extensions.

```kotlin
// build-logic/src/main/kotlin/my-project.java-library.gradle.kts
plugins {
    `java-library`
}

java {
    withSourcesJar()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Apply the convention in a project:

```kotlin
// app/build.gradle.kts
plugins {
    id("my-project.java-library")
}
```

**Default:** Keep a convention focused on one coherent policy, use typed lazy APIs such as `configureEach`, and let the consumer add only genuinely project-specific configuration. Use `.gradle.kts` for standard precompiled scripts; for settings or initialization scripts, use the `.settings.gradle.kts` and `.init.gradle.kts` suffixes respectively. Derived plugin IDs follow the filename and package structure.

**Anti-pattern:** Make a convention plugin depend on a particular sibling convention being applied first, configure concrete tasks eagerly, or expose internal Gradle implementation types as the convention API. Ignore the requirement that external plugins used within a precompiled script MUST be declared on the build-logic project's `implementation` classpath (not just the consumer's `plugins {}` block).

**Nuance:** Distinguish "convention" (the policy) from "precompiled script" (the implementation mechanism). Precompiled scripts provide an easy path to convention plugins but are subject to Groovy package limitations if using `.gradle` files.

## buildSrc vs build-logic

Use an included build (`build-logic`) for reusable build logic in most projects. While `buildSrc` is the "simplest" setup, any change in `buildSrc` invalidates the entire build's configuration cache and task inputs, whereas an included build provides narrower invalidation boundaries.

**Default:** Favor `build-logic` as the standard for scalability and performance. For very small projects where build-logic rarely changes, `buildSrc` is a valid fallback.

**Anti-pattern:** Use `buildSrc` for large, complex builds with frequent logic changes, or use it as a replacement for a binary plugin when the logic needs to be shared across multiple unrelated repositories.

See [Favor `build-logic` composite builds for build logic](best-practices/favor-build-logic-composite-builds-for-build-logic.md) for the full trade-off analysis.

## Plugin Application Ordering

Plugin application is deterministic, but sibling convention plugins do not provide a safe global ordering contract. A convention plugin that reads an extension or task created by another plugin must react to that plugin's application event.

```kotlin
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        compilerOptions {
            // Configure only after the Kotlin JVM plugin creates this extension.
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        // Configure only realized or future matching tasks.
    }
}
```

**Default:** Use `pluginManager.withPlugin("plugin.id") { configurePlugin() }` for dependent configuration, then use `configureEach` and providers inside the callback.

**This is prohibited:** Assume the order of `plugins {}` entries or sibling convention plugins is a reusable synchronization mechanism, call `afterEvaluate { configurePlugin() }` to repair that assumption, or call `Provider.get()` while configuring unrelated work.

**Nuance:** A "legacy application fallback" (applying a plugin by type or via `apply(plugin = "...")`) may occasionally be necessary for older third-party plugins that lack valid plugin metadata/descriptors, but this is a resolution fix, NOT a way to control application ordering.

A plugin callback runs when the target plugin is applied and also handles the case where it was already applied. Configure only public extensions and tasks exposed by that plugin.

### Version notes

- **Gradle 9.x:** Prefer the build-logic composite-build pattern and current precompiled script plugin guidance. Ordering remains an implementation detail, not a convention-plugin contract.
- **Gradle 8.x:** The same `withPlugin`, provider, and lazy-task pattern applies. Verify plugin versions and Kotlin DSL accessors against the wrapper.
- **Gradle 7.x:** Precompiled script plugins and `withPlugin` are available, but older third-party plugins may require legacy application fallback when they lack usable plugin metadata. Do not use legacy application to solve ordering.

**More info:**

- Composite build and conventions: `gradle_docs` `tag:userguide`, path `userguide/implementing_gradle_plugins_convention.md`; published guide: https://docs.gradle.org/current/userguide/implementing_gradle_plugins_convention.html
- Build structure: `gradle_docs` `tag:best-practices`, path `userguide/best_practices_structuring_builds.md`, term `Use Convention Plugins`; published guide: https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html
- Plugin reactions: `gradle_docs` `tag:dsl`, path `dsl/org.gradle.api.plugins.PluginManager.md`, term `withPlugin`; published reference: https://docs.gradle.org/current/dsl/org.gradle.api.plugins.PluginManager.html
- Gradle documentation lookup: https://gradle-mcp.rnett.dev/latest/tools/GRADLE_DOCS_TOOLS/
