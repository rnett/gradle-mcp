# Extensions

Procedural guide to creating, getting, and working with Gradle extensions (`ExtensionContainer`). Extensions expose a stable configuration API for a plugin; keep them lazy and managed so they remain configuration-cache and isolated-projects compatible.

## Creating extensions

Create an extension from plugin code with `extensions.create`, backed by a managed-property class or interface:

```kotlin
interface GreetingExtension {
    val message: Property<String>
    val outputDir: DirectoryProperty
}

class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<GreetingExtension>("greeting")
        extension.message.convention("Hello")
        extension.outputDir.convention(project.layout.buildDirectory.dir("greeting"))

        project.tasks.register<GreetingTask>("greet") {
            message.set(extension.message)
            outputDir.set(extension.outputDir)
        }
    }
}
```

**Default:** declare extension properties as abstract managed properties (`Property<T>`, `DirectoryProperty`, `ConfigurableFileCollection`, nested managed types, `NamedDomainObjectContainer`). Use a single `extensions.create` per plugin with a short, namespaced name.

**Anti-pattern:** exposing mutable `var` fields on the extension (Gradle cannot track them), copying declared property values eagerly, or holding a `Project` reference inside the extension object.

For `NamedDomainObjectContainer`-backed extensions (named objects configured in a block), see [Collections and Containers](managed-types-and-providers.md#collections-and-containers).

## Getting extensions

Read an applied extension from the `ExtensionContainer`:

```kotlin
// Type-safe: works when the container knows the type
val greeting = project.extensions.getByType<GreetingExtension>()

// By name for a dynamically registered extension (no static type binding)
val some = project.extensions.getByName("someExtension") as SomeExtension

// Convenience for a single extension instance of the type (throws if absent)
val only = project.extensions.getByType<OnlyExtension>()
```

Within a build script, the generated type-safe accessor is available after the `plugins {}` block is applied (`greeting { ... }`); prefer the accessor over `getByName` for readability. In plugin code (no accessor), use `getByType` when the type is unique and known, and `getByName` only when you must address a non-typed or multi-name extension.

**Default:** `getByType` in plugin code; the generated accessor or `extensions.configure` in scripts. React to plugin presence with `pluginManager.withPlugin("id"){ ... }` rather than `getByName` before application is guaranteed.

**Anti-pattern:** `getByName` to retrieve an extension whose type is known (loses type safety and can throw obscurely), or assuming an extension exists before the providing plugin applied.

## Working with extensions

Wire extension properties into task properties **via providers**, not eager copies:

```kotlin
project.tasks.register<GreetingTask>("greet") {
    message.set(extension.message)    // wired lazily; later convention/set propagate
    outputDir.set(extension.outputDir)
}
```

Because `message` and `outputDir` are `Property`/`DirectoryProperty`, `set(...)` links the providers: any later change to the extension (including a user config in their build script) flows into the task without re-copying.

**Convention/default values:** set `convention(...)` defaults in plugin `apply` so a user can override them with `set(...)`. Prefer `convention` over `set` for values that should remain overridable, and `finalizeValueOnRead()` when a value must stop changing at a deliberate boundary.

**Configuration-cache consequence:** wiring providers (not resolved values) keeps the task's inputs connected to the extension lazily, so the configuration-cache entry records the provider chain rather than a frozen value. Do not call `.get()` on an extension property during plugin `apply`.

**Anti-pattern:** copying `extension.message.get()` into a task at configuration time, which realizes the value early and severs the provider relationship.

## Anti-patterns

- **Eager realization at configuration time:** reading/resolving extension properties during `apply` to compute other values. Use `map`/`flatMap`/provider wiring instead.
- **Holding `Project` in an extension object:** the extension outlives the plugin apply; retaining `Project` couples it to the live model and breaks the configuration cache. Derive paths from injected `ProjectLayout` or captured providers instead.
- **Leaking internal implementation types** into the public extension API; keep the surface minimal and stable.

**Default:** keep extensions thin: a set of managed properties plus conventions, wired into tasks by provider references.

## More info

- Implementing and using extensions: `gradle_docs(path="userguide/implementing_gradle_plugins.md")`
- Lazy configuration and providers: [Managed Types and Providers](managed-types-and-providers.md)
- Binary plugin anatomy and TestKit: [Plugin Development](plugin-development.md)
- Gradle documentation lookup: `gradle_docs`
