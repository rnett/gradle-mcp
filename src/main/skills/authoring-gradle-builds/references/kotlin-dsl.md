# Kotlin DSL

Prefer Kotlin DSL for new build authoring. It provides static typing, IDE-assisted navigation, and better maintainability than Groovy. Use Groovy only when maintaining legacy builds or when a specific plugin requires it.

## Type-Safe Accessors

Gradle generates type-safe accessors for plugins and extensions to avoid string-based lookups.

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
}

// Generated accessor 'kotlin' is available immediately after the plugins block
kotlin {
    jvmToolchain(21)
}
```

**Default:** Apply plugins using the `plugins {}` block to ensure accessors are generated. Use the generated accessors for a type-safe experience.

**Anti-pattern:** Expect accessors to be available for plugins applied via `apply(plugin = ...)` or reflectively, or attempt to use accessors before the `plugins {}` block has been evaluated.

See [Apply plugins using the plugins block](best-practices/apply-plugins-using-the-plugins-block.md) for the approved pattern.

## Deprecated Kotlin `by` delegates

The Kotlin DSL container-property delegates — `by creating`, `by getting`, and their container-property variants — are formally deprecated. Replace them with the lazy `register`/`named` APIs on the container's `NamedDomainObjectProvider`, which avoid realizing the domain object during configuration.

**Deprecation scope:** only the Kotlin DSL `by` delegates (`by creating`, `by getting`, `by registering`, `by named`, etc.) for container properties are deprecated. The underlying container methods `create`/`getByName` are NOT formally deprecated — calling `container.create("name")` or `container.getByName("name")` remains valid — but they are avoided in favor of `register`/`named` because the lazy forms preserve configuration-avoidance.

**Do this:**

```kotlin
tasks.register("myTask") { ... }              // lazy; task not realized until needed
val jvmMain = named("jvmMain")                // lazy reference to an existing object
val commonDependencies = register("commonDependencies") // lazy creation of a container element
```

**Do not do this:**

```kotlin
val myTask by creating { ... }   // deprecated delegate syntax
val jvmMain by getting           // deprecated delegate syntax
```

**Default:** use `register` to create a container element lazily and `named` to reference an element that already exists. Use `getByName`/`create` only when a lazy form is genuinely not applicable (rare in normal authoring).

**Configuration-cache consequence:** `register`/`named` return `NamedDomainObjectProvider` handles that defer realization, keeping the model lazy and configuration-cache/isolated-projects compatible. Eager `getByName`/`create` in hot configuration paths realize objects early and add configuration-time cost even for tasks that are never run.

## Receivers and Nested-Lambda Ambiguity

Kotlin DSL uses receivers to provide a concise syntax. However, nested lambdas can shadow receivers, leading to ambiguity or incorrect target calls.

```kotlin
tasks.register("myTask") {
    // 'this' is the Task receiver
    doLast {
        // 'this' is now the TaskAction closure, but the Task receiver is still accessible
        println("Executing task ${this@register.name}")
    }
}
```

**Default:** Use qualified receivers (`this@label`) when working within nested lambdas to ensure you are calling methods on the intended object.

**Anti-pattern:** Rely on implicit receiver resolution in deep nesting, which can lead to "invisible" bugs where a method is called on the wrong object.

## Script Naming and Placement

Follow standard naming conventions for script files to ensure Gradle recognizes them correctly.

- **Project Build:** `build.gradle.kts`
- **Settings:** `settings.gradle.kts`
- **Precompiled Scripts:** Files named `*.gradle.kts` in `src/main/kotlin` of a plugin project (cross-ref [Convention Plugins](convention-plugins.md)).

**Default:** Use the `.kts` extension for all Kotlin scripts. Place `settings.gradle.kts` in the root directory.

## Use Public API Only

The Kotlin DSL provides a set of public APIs for build authoring. Many "convenience" methods found in internal Gradle classes are unstable and break during minor version upgrades.

**This is prohibited:** Use internal Gradle APIs (those in packages marked `.internal.` or without public documentation) in production build logic.

`.gradle.kts` scripts cannot reference `org.gradle.internal.impldep.*`; build scripts compile against the prebuilt public API JAR, which excludes the relocated internal implementation dependencies.

See [Do not use internal APIs](best-practices/do-not-use-internal-apis.md) for the stability rationale.

## IDE Import Behavior

Accessors and type-safe extensions are not available until the IDE has successfully imported and synchronized the Gradle project.

- **Sync Required:** After adding a new plugin to the `plugins {}` block, you must trigger a Gradle reload/sync in your IDE for the new accessors to appear.
- **Stale Accessors:** If a plugin is removed or changed, the IDE may show stale accessors until the next sync.

**Default:** Trigger a project sync after any change to the `plugins {}` block or `settings.gradle.kts`.

## Limitations vs Groovy

While Kotlin DSL is preferred, Groovy remains more dynamic in specific areas:
- **Dynamic Dispatch:** Groovy can resolve methods that don't exist at compile time, which is sometimes used in legacy plugins.
- **Syntax Verbosity:** Some extremely complex configuration blocks are more concise in Groovy.

**Default:** If a piece of logic is impossible to express in Kotlin due to a plugin's dynamic nature, move that specific logic into a separate Groovy build script or use the `apply(from = "...")` mechanism.

### Version notes

- **Gradle 9.7:** Embedded Kotlin moved from 2.3.21 to 2.4.0; `-language-version=1.9` is no longer supported for build scripts (minimum compile target is 2.0); the oldest supported `kotlin-dsl` plugin is 6.2.0 (requires at least Kotlin Gradle Plugin 1.9.22).
- **Gradle 9.x:** Kotlin DSL is the first-class citizen. Accessor generation is highly optimized.
- **Gradle 8.x:** Full support for Kotlin DSL; `compilerOptions` alignment became the standard for Kotlin plugins.
- **Gradle 7.x:** Kotlin DSL was stable, but early versions had slower sync times. Accessor timing constraints were similar to current versions.

**More info:**
- Gradle Kotlin DSL Primer: `gradle_docs(path="userguide/kotlin_dsl.md")`

**Cross-references:**
- For build logic sharing via scripts, see [Convention Plugins](convention-plugins.md).
- For configuring Kotlin target versions, see [Kotlin Compiler Options](kotlin-compiler-options.md).
- For the overall build flow, see [Build Lifecycle](build-lifecycle.md).
