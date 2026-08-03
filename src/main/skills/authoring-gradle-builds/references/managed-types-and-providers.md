# Managed Types and Providers

Model build properties lazily using the Property and Provider APIs. Wire values through providers to defer realization until the execution boundary; this ensures configuration avoidance and compatibility with the configuration cache.

## Property vs Provider

Use `Property<T>` for values that can be configured (mutable) and `Provider<T>` for values that are read-only. Wire them using `set()`, `map`, `flatMap`, `zip`, and `orElse`.

```kotlin
abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val message: Property<String>

    @get:Input
    abstract val suffix: Property<String>

    // Computed property wired lazily
    val fullMessage: Provider<String> = message.flatMap { msg ->
        suffix.map { s -> "$msg$s" }
    }
}

tasks.register<MyTask>("myTask") {
    message.set("Hello")
    suffix.convention(" World")
}
```

**Default:** Use `Property` for task and extension inputs. Wire multiple providers using `zip` for synchronous combinations or `flatMap` for dependent transformations.

**Anti-pattern:** Call `.get()` or `.getOrElse()` during the configuration phase to compute another property; use `map` or `flatMap` instead.

### `ProviderFactory.provider` is a configuration-time bridge

`ProviderFactory.provider { }` (and `Project.provider { }`) wraps a computation in a `Provider`, but the lambda is a configuration-time bridge and may be evaluated eagerly. Do not use it to defer file I/O, network calls, process execution, or other execution work; use a task input and read it in the task action instead.

```kotlin
val configuredValue = providers.provider { readConfigurationFile() } // Configuration-time computation
// For execution work, wire a task input and call the injected service from @TaskAction.
```

See [Do not call `get()` on a Provider outside a Task action](best-practices/do-not-call-get-on-a-provider-outside-a-task-action.md) for the rationale on avoiding premature realization.

### Field-guide rule: Distinguish `set(null)` from an absent provider

## set vs convention vs finalization

Use `convention()` to define a default that can be overridden and `set()` to provide a fixed value or wire a provider. Prefer `finalizeValueOnRead()` when the property should become fixed at its first read, or `disallowChanges()` when configuration should close at a deliberate boundary. Use `finalizeValue()` only when immediate finalization and disposal of the value's source are intentional.

```kotlin
abstract class MyExtension {
    abstract val outputDir: DirectoryProperty
}

// In plugin apply logic:
extension.outputDir.convention(layout.buildDirectory.dir("outputs"))
extension.outputDir.set(layout.projectDirectory.dir("custom-out")) // Overrides convention
extension.outputDir.finalizeValueOnRead() // Finalizes when first read
// Use disallowChanges() when the configuration boundary is explicit.
```

**Default:** Apply conventions in plugin code so users have a sensible default. Prefer `finalizeValueOnRead()` or `disallowChanges()` according to when the value must stop changing; reserve `finalizeValue()` for deliberate immediate finalization.

**Anti-pattern:** Use `set()` for defaults, which prevents users from providing their own values without knowing the original default, or finalize immediately merely to prevent later configuration.

## Scalar, File, and Collection Properties

Use specialized managed types for files and collections to enable precise input/output tracking and lazy resolution.

```kotlin
abstract class FileProcessorTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:Input
    abstract val flags: MapProperty<String, Boolean>

    @get:Input
    abstract val tags: SetProperty<String>

    @get:Input
    abstract val orderedItems: ListProperty<String>
}
```

**Default:** Always use `RegularFileProperty` and `DirectoryProperty` instead of `File` or `String` for paths. Use `ListProperty`, `SetProperty`, and `MapProperty` for managed collections.

**Anti-pattern:** Declare file inputs as `Property<File>` or `Property<String>`, which bypasses Gradle's optimized file-system tracking and prevents correct cache key computation.

## Managed and Named Managed Types

Prefer Gradle-created managed objects over manual instantiation. Use `ObjectFactory` to create nested managed types or named objects.

```kotlin
abstract class MyExtension {
    abstract val settings: MySettings // Nested managed type
}

abstract class MySettings {
    abstract val timeout: Property<Int>
}

// For named objects
abstract class MyNamedDomainObject {
    abstract val name: String
    abstract val value: Property<Int>
}
```

**Default:** Declare managed properties as `abstract` in classes inheriting from `DefaultTask` or other managed types. Use `NamedDomainObjectContainer` to manage a collection of uniquely named objects.

**Anti-pattern:** Use `new MySettings()` or `MySettings()` inside a task or extension; this breaks the managed lifecycle and configuration cache compatibility.

**Field-guide rule: Use provider-backed managed model types.** Abstract managed properties created through Gradle's `ObjectFactory` preserve lifecycle, validation, and caching semantics that ordinary mutable fields do not.

## Collections and Containers

Use `NamedDomainObjectContainer` for polymorphic collections of named objects. Use `register` and `configureEach` to keep the container lazy.

```kotlin
interface MyExtension {
    val items: NamedDomainObjectContainer<MyItem>
}

// Usage in build script:
extensions.configure<MyExtension> {
    items.register("first") {
        value.set(10)
    }
    items.configureEach {
        // Configures all items lazily
    }
}
```

**Default:** Use `register` over `create` to avoid eager realization of every object in the container. Use `configureEach` instead of `allItems` or `all` to maintain configuration avoidance.

**Anti-pattern:** Iterate over a container using `forEach` or `toList()` during configuration, which forces eager realization of every managed object.

## Eager to Lazy Replacement

Replace eager APIs with their lazy counterparts to defer realization until the execution boundary. Each row lists the configuration-time consequence of staying eager.

| Eager API | Lazy replacement | Configuration-time consequence of staying eager |
| :--- | :--- | :--- |
| `tasks.create("name") { }` / `container.create("name")` | `tasks.register("name") { }` / `container.register("name") { }` | The object is realized and configured immediately even when unselected; breaks configuration avoidance |
| `getByName("name")` / `getByType<X>()` on a container | `named("name") { }` / `named<X>()` | Resolves the object eagerly, forcing realization before it is needed |
| `all { }`, `forEach`, or `toList()` over a container/collection | `configureEach { }`, or provider/`FileCollection`-based consumption | Iteration forces eager realization of every element during configuration |
| `Project.file("...")` / `new File(...)` | `layout.projectDirectory.file("...")` / provider-backed paths | Resolves a realized path against the current environment instead of staying lazy |
| Direct cross-project output wiring (`project(":other").tasks["jar"]`) | consumable configuration + dependency resolution | Eagerly realizes the other project and breaks isolated-projects compatibility |

**Configuration-cache consequence:** every eager call above performs work during configuration that must be deferred; the configuration cache and isolated projects reject or freeze this behavior. Prefer `register`/`named`/`configureEach` and provider wiring everywhere.

## Lazy Files

Use `ProjectLayout` to resolve project-relative paths lazily. Avoid `new File()` as it resolves against the current working directory (CWD), which varies by execution environment.

```kotlin
val buildDir = layout.buildDirectory // Provider<Directory>
val projectDir = layout.projectDirectory // Directory

// Safe relative path
val inputFile = layout.projectDirectory.file("config/settings.json")

// Provider-backed copy wiring
tasks.register<Copy>("copyConfig") {
    from(inputFile)
    into(layout.buildDirectory.dir("config"))
}
```

**Default:** Use `layout.projectDirectory` and `layout.buildDirectory` for all file paths. Use `FileCollection` and `FileTree` for grouping files without resolving them to a list.

**Anti-pattern:** Use `new File("path/to/file")` or `Project.file("...")` inside a task action; these are not lazy and can cause issues in isolated projects or remote cache restores.

### `RegularFile`/`Directory` providers vs realized `File`/`Path`

Model every path as a provider-backed `RegularFileProperty`/`DirectoryProperty` (mutable task/extension input or output) or a `Provider<RegularFile>`/`Provider<Directory>` (read-only view derived from `layout`). Resolve to a concrete `File`/`Path` only inside a task action, never in configuration.

| Form | Nature | Use when |
| :--- | :--- | :--- |
| `RegularFileProperty` / `DirectoryProperty` | Lazy, managed, tracked by Gradle | Task inputs/outputs and extension properties |
| `Provider<RegularFile>` / `Provider<Directory>` | Lazy read-only view | Wiring derived paths, e.g. `layout.buildDirectory.dir(...)` |
| Realized `File` / `Path` | Eager, absolute, resolved now | Inside a task action or as a fixed static constant |

**Configuration-cache consequence:** capturing a realized `File` or the `Project` object in a task field or extension freezes a value that must stay lazy (or is illegal) under the configuration cache and isolated projects. Keep file values as providers and resolve them only at an execution boundary.

### Lazy file trees and archive trees

Use `fileTree`, `zipTree`, and `tarTree` to model collections of files without resolving them eagerly:

```kotlin
val srcTree = layout.projectDirectory.dir("src").asFileTree
val exploded = zipTree(layout.projectDirectory.file("lib.zip"))
val tarContents = tarTree(layout.projectDirectory.file("bundle.tgz"))
```

**Archive-tree laziness:** `zipTree`/`tarTree` do **not** expand the archive at configuration time; the archive's contents are discovered when the consuming task executes (or the tree is resolved). Keep large archives off the configuration path and preserve config-cache/IP compatibility.

**Build-cache consequence:** feeding an archive tree as a task input (`from(zipTree(...))` or `@InputFiles`) means Gradle hashes the *contents* of the archive; a changed archive invalidates the cache. Do not copy or re-emit the archive as a whole unless the archive file itself is the intended input.

**Anti-pattern:** expanding an archive to disk during configuration, or iterating a tree at configuration time to build a file list.

### `ConfigurableFileCollection` vs `FileCollection`

| Type | Nature | Use when |
| :--- | :--- | :--- |
| `ConfigurableFileCollection` | Lazy **and mutable**; you add sources with `from(...)` | A task/extension property assembled during configuration and resolved at execution |
| `FileCollection` | Read-only view of a collection | Consuming an already-built collection you only read (e.g. a task input) |

**Configuration-cache consequence:** never iterate or resolve a collection at configuration time; resolve only inside a task action. See [File Operations](file-operations.md) for the full `Copy`/`Sync`/`Delete` recipes and file-tracking guidance.

See [Avoid using eager APIs on File Collections](best-practices/avoid-using-eager-apis-on-file-collections.md) for the rationale on lazy file collections.

## Lazy Producer/Consumer Recipe

Produce an artifact in one project and consume it in another through configurations, keeping everything provider-backed and isolated-projects-compatible. Do **not** use `project(path, configuration)` or a cross-project task dependency.

### 1. Producer: task with a provider-backed output

```kotlin
// producer/build.gradle.kts
abstract class GenerateConfig : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val version: Property<String>

    @TaskAction
    fun generate() {
        outputFile.get().asFile.writeText("version=${version.get()}")
    }
}

val generateConfig = tasks.register<GenerateConfig>("generateConfig") {
    outputFile.set(layout.buildDirectory.file("generated/config.properties"))
    version.convention("1.0")
}
```

### 2. Producer: expose the output on a consumable configuration

```kotlin
// producer/build.gradle.kts
val generatedElements = configurations.register("generatedElements") {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
    }
}

artifacts {
    add(generatedElements.name, generateConfig.map { it.outputFile.get().asFile })
}
```

The `generateConfig.map { ... }` wiring attaches the artifact to the registered task's output lazily; `outputFile.get()` runs inside the artifact's own realization, not at configuration time.

### 3. Consumer: resolvable consumption in another project

```kotlin
// consumer/build.gradle.kts
val generatedConfig = configurations.register("generatedConfig") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    generatedConfig(project(":producer"))
}

tasks.register("printConfig") {
    val cfg = generatedConfig
    inputs.files(cfg)
    doLast {
        cfg.get().files.forEach { println(it.readText()) }
    }
}
```

**Isolated-projects consequence:** the consumer asks Gradle to resolve `:producer`'s consumable configuration via normal dependency resolution; no project object is touched directly, so the build stays isolated-projects-compatible. The producer task runs only when its output is actually resolved.

**This is prohibited:** `dependencies { add("implementation", project(path = ":producer", configuration = "generatedElements")) }` or `project(":producer").tasks["generateConfig"]` — the explicit configuration form bypasses variant-aware selection and direct task access breaks isolation. See [Configurations and Variants](configurations-and-variants.md) for the variant-aware recipe.

## Incubating: Dataflow Actions

**Warning:** Dataflow Actions are **incubating** and not a stable default. Use them only for non-task logic that requires annotated managed inputs. For implementation details, use `gradle_docs(path="userguide/dataflow_actions.md")`.

**Default:** Prefer standard tasks for any work that produces file outputs. Dataflow actions are intended for internal Gradle data-flow within the configuration or execution graph.

**Anti-pattern:** Using `FlowAction` for general-purpose build logic or as a replacement for custom tasks.

### Version notes

- **Gradle 9.x:** Property and Provider APIs are the stable standard. Configuration cache is stable; all managed types are required for compatibility. Dataflow actions remain incubating.
- **Gradle 8.x:** The same APIs apply; configuration cache is stable from 8.1.
- **Gradle 7.x:** Managed properties are available but some plugins may still use eager `create` patterns. Prefer `register` and `Property` where supported.

**More info:**
- Lazy configuration: `gradle_docs(path="userguide/lazy_configuration.md")`
- Properties and Providers: `gradle_docs(path="userguide/properties_providers.md")`
- Collections: `gradle_docs(path="userguide/collections.md")`
- Working with files: `gradle_docs(path="userguide/working_with_files.md")`
- Dataflow actions: `gradle_docs(path="userguide/dataflow_actions.md")`
