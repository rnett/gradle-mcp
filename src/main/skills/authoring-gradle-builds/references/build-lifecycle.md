# Build Lifecycle

Understand the three primary phases of a Gradle build and how the task graph is constructed. Top-level script code runs during initialization and configuration; only task actions execute during the execution phase.

## The Three Phases

Gradle builds proceed through three distinct phases in every execution.

### 1. Initialization
Gradle determines which projects will participate in the build. It evaluates `settings.gradle(.kts)` files and creates `Settings` and `Project` objects for every project.

### 2. Configuration
Gradle evaluates the build scripts and constructs the project model.

**Task Graph Calculation:**
After configuration is complete (and before execution begins), Gradle calculates the requested task graph based on the tasks requested on the command line and their dependencies.

**Configuration Cache (Optional):**
When enabled, Gradle can skip the configuration phase entirely by reusing a serialized version of the project model from a previous run. This serialization occurs after configuration finishes if the cache is being populated.

### 3. Execution
Gradle determines the subset of tasks that must run based on the requested task path and their dependencies. It then executes the task actions in the order determined by the task graph.

```kotlin
// This runs during CONFIGURATION (Phase 2)
println("Configuring project ${project.name}...")

tasks.register("echoLifecycle") {
    doLast { 
        // This runs during EXECUTION (Phase 3)
        println("Executing echoLifecycle task!") 
    }
}
```

**Default:** Keep build scripts focused on registering tasks and wiring providers. Only perform actual work inside `@TaskAction` or `doLast` blocks.

### Field-guide rule: Keep expensive work out of configuration

All participating scripts are evaluated while Gradle builds the task graph, so file I/O, network calls, process execution, and CPU-heavy work for an unselected task still run. Register the task and wire its inputs during configuration, then perform the work at the execution boundary.

```kotlin
tasks.register("generate") { doLast { generateExpensiveOutput() } }
// Don't: output = generateExpensiveOutput()
```

**Anti-pattern:** Perform expensive computations, network calls, or file system modifications at the top level of a build script. This slows down every build, including those where the task is not executed.

See [Avoid expensive computations in the configuration phase](best-practices/avoid-expensive-computations-in-configuration-phase.md) for the performance rationale.

## Task Graph Is a DAG

The order of task execution is determined by a Directed Acyclic Graph (DAG) derived from explicit dependencies and implicit data flow, NOT the order of declaration in the script.

```kotlin
tasks.register("taskB") {
    dependsOn("taskA")
}

tasks.register("taskA") {
    doLast { println("Task A") }
}

// Even though taskB is declared first, taskA executes first.
```

**Default:** Define task order using `dependsOn`, `mustRunAfter`, `shouldRunAfter`, or by wiring inputs and outputs.

**Anti-pattern:** Assume that tasks will run in the order they appear in the `.gradle.kts` file. Parallel execution (via `--parallel`) further decouples execution order from declaration order.

## Hook Ordering

Lifecycle hooks allow you to react to build events, but they are often misused as ordering repairs.

### Configuration-time Hooks

- `project.afterEvaluate { ... }`: Fires after the current project's build script has been fully evaluated.
- `gradle.taskGraph.whenReady { ... }`: Fires after the full task graph has been calculated but before any task executes.
- `gradle.beforeProject { ... }` / `gradle.afterProject { ... }`: React to project configuration callbacks.

**This is prohibited:** Use `afterEvaluate` to fix "task not found" errors or to synchronize model state across projects. Prefer `pluginManager.withPlugin`, lazy `Property` wiring, or the `Provider` API.

**Callback contract:** `beforeProject`, `afterProject`, and related callback ordering are implementation details, not dependency-injection contracts. Do not rely on their timing to make values or services appear in another project's model.

**Version-sensitive field-guide rule:** Read `gradle/wrapper/gradle-wrapper.properties` before applying the explicit avoid-`afterEvaluate` recommendation. The general lifecycle warning applies across 9.x, while the explicit best-practice entry was added in Gradle 9.6.0.

### Execution-time Listeners
- `TaskExecutionListener` / `TaskActionListener`: Provide callbacks during task execution.
- `gradle.buildFinished`: A legacy listener that fires after the build completes.

**Default:** Use `BuildService` for shared state and cleanup. Lifecycle callbacks should be used for reporting or environmental setup, not for driving execution logic.

### Lifecycle APIs and Build Services

Advanced build logic uses `BuildService` and incubating `FlowAction` to handle cross-task state and non-task work.

- **Build Services:** Registered during configuration, these provide a thread-safe way to share state across tasks without capturing `Project` instances.
- **Flow Actions:** (Incubating) Allow for structured work that occurs outside the standard task graph. Use `gradle_docs` with `tag:userguide` and path `userguide/dataflow_actions.md` for implementation details.

**Default:** Use `BuildService` for resources that must be shared across tasks (e.g., a database connection or a shared worker pool).

**Cross-references:**
- For detailed configuration-cache requirements for services, see [Advanced Configuration](advanced-configuration.md).
- For lazy value modeling, see [Managed Types and Providers](managed-types-and-providers.md).

### Version notes

- **Gradle 9.x:** The three-phase lifecycle is the stable foundation. Configuration cache is stable; task graph calculation follows configuration. Flow actions are incubating.
- **Gradle 8.x:** Configuration cache became stable in 8.1. The phase model remains consistent.
- **Gradle 7.x:** Configuration cache was experimental. `afterEvaluate` was more common but already discouraged in favor of lazy APIs.

**More info:**
- Build Lifecycle: `gradle_docs` `tag:userguide`, path `userguide/build_lifecycle.md`
- Intermediate Build Scripting: `gradle_docs` `tag:userguide`, path `userguide/writing_build_scripts_intermediate.md`

**Handoff:**
Observation of build lifecycle outcomes (e.g., reading logs to see which tasks ran) and running specific tasks belongs to `using-gradle`.
