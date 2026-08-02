<!--
class: authored-local
skill: authoring-gradle-builds
-->
# Advanced Configuration

Use this reference when authoring service-injected tasks or plugins, shared build services, value sources, or configuration-cache-compatible build logic. Prefer the latest Gradle 9.x APIs, but preserve the fallbacks in the compatibility notes when the wrapper targets Gradle 7 or 8.

**Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying version-sensitive advanced-configuration guidance, including parallel-execution settings.
## Non-negotiable defaults

- **Field-guide rule: Use public APIs and injected services only.** Use documented APIs and injected `ObjectFactory`, `ProviderFactory`, `ProjectLayout`, `ExecOperations`, and `FileSystemOperations`; never depend on `.internal` types because upgrade breakage is otherwise hidden.
- Inject documented Gradle services. Do not construct Gradle services, retain `Project` for execution, or call `Project` APIs from task actions.
- Model values with managed `Property` and `Provider` instances. Do not call `Provider.get()` during unrelated configuration; use `map` and `flatMap` to wire values lazily. See [Do not call `get()` on a Provider outside a Task action](best-practices/do-not-call-get-on-a-provider-outside-a-task-action.md).
- Register build services with `registerIfAbsent`, declare every consumer with `usesService`, bound concurrency with `maxParallelUsages`, and close owned resources through `AutoCloseable`.
- Use a `ValueSource` when configuration must deliberately read environment variables, system properties, files, or external command results. Declare every external input as a value-source parameter.
- Treat `@UntrackedTask` as a narrowly justified escape hatch for incompatible task inputs, never as a way to hide undeclared inputs.
- Keep project isolation in [Modules and Settings](modules-and-settings.md). Do not duplicate cross-project isolation rules here.

## Service injection

Gradle owns service instances and supplies them to custom tasks, plugins, extensions, and worker actions through `@Inject`. Inject only public services documented for the current object scope:

- `ObjectFactory` creates managed `Property`, `ListProperty`, `MapProperty`, and nested managed objects.
- `ProviderFactory` creates and combines lazy providers, including environment, system-property, and value-source providers.
- `ProjectLayout` provides project directories and lazy file providers.
- `ExecOperations` runs external processes from execution-time code.
- `FileSystemOperations` performs Gradle-tracked file operations.
- `ArchiveOperations` creates archive trees and archive-backed file views.

### Inject services into a task

Use an abstract task with an injectable constructor. Keep task inputs and outputs as managed properties, and resolve them only in `@TaskAction`.

```kotlin
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class GenerateReportTask @Inject constructor(
    private val objects: ObjectFactory,
    private val exec: ExecOperations,
    private val layout: ProjectLayout,
) : DefaultTask() {
    @get:Input
    abstract val reportName: Property<String>

    @get:OutputFile
    val reportFile: RegularFileProperty = objects.fileProperty().convention(
        layout.buildDirectory.file("reports/${name}.txt")
    )

    @TaskAction
    fun generate() {
        exec.exec {
            commandLine("report-tool", "--name", reportName.get(), "--output", reportFile.get().asFile)
        }
    }
}
```

Register the task lazily and pass providers instead of reading values while configuring the task:

```kotlin
tasks.register<GenerateReportTask>("generateReport") {
    reportName.set(providers.gradleProperty("reportName").orElse("default"))
}
```

### Inject services into a plugin

Use constructor injection in a plugin when the plugin needs Gradle operations or managed-object factories. Do not capture `project` in closures that run during task execution.

```kotlin
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.ExecOperations

class ReportsPlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val layout: ProjectLayout,
    private val fileSystem: FileSystemOperations,
    private val archives: ArchiveOperations,
    private val exec: ExecOperations,
) : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("reports", ReportsExtension::class.java, objects)
        project.tasks.register<GenerateReportTask>("generateReport") {
            reportName.set(extension.reportName)
        }
    }
}
```

The plugin may use the `Project` object during configuration to register extensions and tasks, but task actions must use injected services and declared inputs. Injection is safer than manual construction because Gradle supplies lifecycle-aware, configuration-cache-compatible services and can reject unsupported model access at the boundary.

**Anti-patterns**

```kotlin
// Do not do this in a task action.
doLast {
    project.file("input.txt")
    project.exec { commandLine("tool") }
}

// Do not construct Gradle-managed services yourself.
val operations = DefaultExecOperations(/* internal services */)
```

`@Inject` constructors must be visible to Gradle, and the service must be available in the receiving scope. `ProjectLayout` is project-scoped; do not assume it is available in every worker or action scope. Do not inject internal implementation classes. Do not retain `Project`, `Task`, live Gradle model objects, or mutable project extensions in objects that execute later.

**Version notes**: Public service injection is available throughout Gradle 7, 8, and 9. The available-service list and object scopes can change between versions. For Gradle 7.x, use only services documented by that wrapper's user guide and test the plugin with that exact wrapper. Gradle 8.1 made configuration cache stable for general use; Gradle 7.x and 8.0 require explicit compatibility testing and should not be assumed compatible.

**More info**:

- `gradle_docs`: `tag:userguide`, path `userguide/service_injection.md`, terms `@Inject`, `ExecOperations`, `ProjectLayout`.

## Convention, Finalization, and Combination
The model for conventions and finalization is detailed in [Managed Types and Providers](managed-types-and-providers.md). When authoring services or configuration-cache-safe logic, focus on ensuring that values are wired lazily via providers and that the resulting task state is serializable.

## Build services

```kotlin
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.provider.Property

abstract class ReportServerService : BuildService<ReportServerService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        val endpoint: Property<String>
    }

    // Make all mutable state thread-safe when maxParallelUsages permits concurrency.
    private val client = ReportClient(parameters.endpoint.get())

    fun publish(report: String) {
        client.publish(report)
    }

    override fun close() {
        client.close()
    }
}
```

Register once and declare the usage on every task that obtains the service:

```kotlin
val reportServer = gradle.sharedServices.registerIfAbsent(
    "reportServer",
    ReportServerService::class,
) {
    parameters.endpoint.set(providers.gradleProperty("reportEndpoint").orElse("http://127.0.0.1:9000"))
    maxParallelUsages.set(1)
}

tasks.register<PublishReportTask>("publishReport") {
    service.set(reportServer)
    usesService(reportServer)
}
```

The task exposes the service as an internal, non-input property:

```kotlin
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Internal

abstract class PublishReportTask : DefaultTask() {
    @get:Internal
    @get:ServiceReference
    abstract val service: Property<ReportServerService>

    @TaskAction
    fun publish() {
        service.get().publish("report from ${path}")
    }
}
```

If the target Gradle version does not support the `@ServiceReference` form used by the build, retain the service provider as an `@Internal` property and keep the explicit `usesService(reportServer)` declaration. The `get()` call belongs in the task action, after Gradle has established the service usage.

**Lifecycle and concurrency rules**

- `registerIfAbsent` returns a provider. Registration alone does not create the service; Gradle creates it when a consumer needs it.
- `maxParallelUsages.set(1)` serializes consumers. Set a larger bound only when the service and every resource it wraps are safe for that concurrency level.
- `usesService` declares the task-to-service relationship. Without it, Gradle cannot account for the service's lifecycle or concurrency.
- `AutoCloseable.close()` runs after the last consumer and before the build ends, not when the service is registered. Close sockets, processes, temporary servers, and files there.
- Build services are not task inputs. Do not put output-affecting mutable state in a service and expect incremental execution or build-cache keys to detect it.
- Keep shared state inside the service, not in global singletons. Use immutable parameters and synchronized or concurrent data structures for concurrent consumers.
- Worker actions using classloader or process isolation cannot directly consume a build service. Pass serializable worker parameters or use `noIsolation()` when safe.

### Parallel execution

Enable parallel execution only after removing shared mutable state from build logic and services. Parallel-execution configuration keys are version-dependent, so read the wrapper properties before selecting or applying them; verify the exact key and semantics with `gradle_docs` for that version.

**Anti-patterns**: start external resources during plugin application; use a global singleton; omit `usesService`; set unbounded parallel usage for a non-thread-safe client; or assume `close()` runs immediately after registration.

**Version notes**: Build services are available in Gradle 7, 8, and 9. Annotation and registration details evolve, so verify `@ServiceReference`, provider wiring, and the worker-isolation limitation against the target wrapper. On Gradle 7.x, prefer the explicit `usesService` form when compatibility with newer annotations is uncertain.

**More info**:

- `gradle_docs`: `tag:userguide`, path `userguide/build_services.md`, terms `registerIfAbsent`, `usesService`, `maxParallelUsages`.

## Value sources

A `ValueSource<T, P : ValueSourceParameters>` is the Gradle-managed boundary for reading external configuration while configuring a build. Use `providers.of(...)` to create the provider and call `obtain()` only when the value is intentionally needed during configuration.

Declare all data that can change the result in `ValueSourceParameters`. Gradle can then identify the source's parameters and cache the computed value. Hidden reads are not tracked and can make configuration-cache reuse stale data.

### Environment variable source

```kotlin
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

abstract class EnvironmentValueSource : ValueSource<String, EnvironmentValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val name: Property<String>
    }

    override fun obtain(): String? = System.getenv(parameters.name.get())
}

val ciEnvironment: String? = providers.of(EnvironmentValueSource::class) {
    parameters.name.set("CI")
}.obtain()
```

Use a provider instead of `obtain()` when the value can remain lazy:

```kotlin
val ciEnvironmentProvider = providers.of(EnvironmentValueSource::class) {
    parameters.name.set("CI")
}

tasks.register<GenerateReportTask>("generateReport") {
    reportName.set(ciEnvironmentProvider.map { value -> "ci-$value" }.orElse("local"))
}
```

For system properties, model the property name in the parameters and read it inside `obtain()`. For files, use a `RegularFileProperty` or `DirectoryProperty` parameter and read only the declared location. For an external command, declare every argument and working-directory input, execute it inside `obtain()`, and return only stable, normalized output. Prefer Gradle's ordinary `ProviderFactory` methods when they already model the source you need.

**Value-source caching gotchas**

- Caching follows declared parameters, not arbitrary state read by `obtain()`. A changing environment variable, file outside the declared path, current time, network response, or undeclared command input can invalidate correctness without invalidating the cached configuration model.
- `obtain()` is deliberately a configuration-time calculation. It does not make arbitrary `Provider.get()` calls safe elsewhere. Keep normal providers lazy and wire them into task properties.
- Do not read `System.getenv`, `System.getProperty`, or files directly from build logic when a value source or Gradle provider can model the input.
- Do not use `@UntrackedTask` to conceal an external read. Use it only when the task is genuinely incompatible with Gradle's input tracking and document the correctness trade-off.
- Do not put secrets in logs or outputs. Model credentials through Gradle's supported credential mechanisms and keep secret values out of task outputs and diagnostic messages.

**Anti-pattern**:

```kotlin
// Hidden configuration-cache input and eager external work.
val branch = System.getenv("GIT_BRANCH")
val settingsText = file("settings.json").readText()
```

**Version notes**: The verified evidence for this section is Javadoc-only: `ValueSource`, `ValueSourceParameters`, and `ProviderFactory.of` are public API types or methods in the cited Gradle API documentation. This does not verify a version-scoped user-guide page or establish identical behavior across Gradle 7, 8, and 9. Treat availability and configuration-cache semantics as wrapper-specific. For older Gradle 7.x builds, use ordinary providers and explicit task inputs as the fallback when the target wrapper does not expose the required value-source API or parameter type.

**More info**:

- `gradle_docs`: `tag:javadoc`, paths `kotlin-dsl/gradle/org.gradle.api.provider/-value-source/index.md`, `kotlin-dsl/gradle/org.gradle.api.provider/-value-source-parameters/index.md`, `kotlin-dsl/gradle/org.gradle.api.provider/-provider-factory/of.md`, and `kotlin-dsl/gradle/org.gradle.api.provider/-value-source/obtain.md`.
- Version-scoped handoff: verify the target wrapper with `gradle_docs` `tag:javadoc ValueSource` and `tag:userguide "value sources"` (or `ValueSource ValueSourceParameters providers.of obtain`) before relying on a user-guide path or cross-version behavior claim.

## Configuration-Cache Requirements Matrix
The configuration cache requires that the task graph be serializable. Any violation of these requirements is reported as a "problem" and causes the build to fail.

**Disallowed Live State:** Tasks must not capture or retain references to the following live model objects:
- `Project`, `Task`, `Settings`, `SourceSet`, or `Configuration`.
- Any object that maintains a reference back to the `Project`.
- Ordinary shared mutable objects or JDK synchronization primitives across task instances.

**Safe Alternatives:** Convert live model state into serializable types before storing them in task properties:
- Use `Property<T>`, `Provider<T>`, `RegularFileProperty`, `DirectoryProperty`, or `FileCollection`.
- Use `Provider` to defer the resolution of model state until the execution boundary.

**Configuration-Cache Restrictions:**
- **Task Extensions/Conventions:** Must be wired lazily. Eagerly realizing a value during configuration for a convention breaks the cache.
- **Listeners:** Build listeners (e.g., `TaskExecutionListener`) are generally prohibited if they capture non-serializable state.
- **External Processes & Agents:** Bytecode agents and external processes launched during configuration are disallowed.
- **System/Environment Reads:** `System.getenv()` and `System.getProperty()` are dangerous if unmodeled. Use a `ValueSource` to declare these as explicit configuration-time inputs.
- **Undeclared File Reads:** Any file read during configuration must be declared via a `ValueSource` or `Provider`.
- **Secret Handling:** Do not store secrets or encryption keys in the configuration cache. Use Gradle's provider-based credential mechanism.

**Build Services for State:** Use a `BuildService` for cross-task state. Declare the relationship with `usesService`, define `maxParallelUsages` to bound concurrency, and keep parameters immutable.

**Default:** Treat configuration as a pure declaration. If a value must be read from the environment or filesystem during configuration, wrap it in a `ValueSource`.

**Anti-pattern:** Capturing `project` in a task property or using `doLast { project.exec { ... } }`.

## Debugging the Configuration Cache
The configuration cache is validated during the "storing" phase (after configuration) and the "loading" phase (before execution).

**HTML Problem Report:** When a problem is detected, Gradle generates an HTML report pinpointing the exact non-serializable object and the path to the violation. Use this report as the primary diagnostic tool.

**Execution Flow:**
1. **Storing:** Gradle evaluates the build and attempts to serialize the task graph.
2. **Loading:** On subsequent runs, Gradle skips configuration and deserializes the graph directly.

**Integrity Checks:** Gradle verifies that the inputs used for the cache key have not changed. If they have, the cache is invalidated and the build is re-configured.

**The Warning Boundary:** Use `--configuration-cache-problems=warn` during migration. This treats violations as warnings rather than failures, allowing you to identify problems without blocking execution. However, this is a migration aid, not a compliance target.

**Testing with TestKit:** Use `GradleRunner` to verify configuration-cache compatibility in functional tests. Configure the runner with `.withArguments("--configuration-cache")` to ensure the build logic is compatible with serialization.

**Strict Mode:** Use strict-mode flags when a build must guarantee compatibility for downstream consumers.

### Version notes
- **Gradle 9.0:** Unsupported provider event handling is now an error unless handled by approved `BuildService` providers.
- **Gradle 8.1:** Configuration cache is stable and ready for general use.
- **Gradle 8.0:** Pre-stable; requires explicit compatibility testing.
- **Gradle 7.x:** Incubating; requires an explicit opt-in and manual validation. For Gradle 7.x builds, use ordinary providers and explicit task inputs as the fallback when the target wrapper does not expose the required `ValueSource` or `BuildService` APIs.

**More info:**
- `gradle_docs`: `tag:userguide`, path `userguide/configuration_cache_requirements.md`
- `gradle_docs`: `tag:userguide`, path `userguide/configuration_cache_debugging.md`
- `gradle_docs`: `tag:userguide`, path `userguide/configuration_cache_enabling.md`
- Frozen rationale: [Use the Configuration Cache](best-practices/use-the-configuration-cache.md) (provides usage and enablement rationale; hand off actual enabling to `using-gradle`).
- Handoff: Enabling the configuration cache in `gradle.properties` or via CLI, reading runtime outcomes, and general failure diagnosis belongs to `using-gradle`.
