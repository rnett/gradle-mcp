# Tool and Skill Improvement Plan - Specification

Based on my review of the current tools and skills, I have developed a plan to consolidate the toolset and leverage the **Progressive Disclosure** principle in the skills. This approach reduces tool bloat, simplifies the agent's
decision-making process, and provides expert guidance only when needed.

## 1. Tool Consolidation Strategy

I will consolidate the current 25+ granular tools into **6 high-level, flexible tools**. This follows the "Thin Tool, Fat Skill" philosophy by moving complexity from the tool definitions into the skill-based workflows.

| Category         | New Consolidated Tool         | Replaces                                                                                                                                                                                                                    |
|:-----------------|:------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Execution**    | `gradle_execute`              | `run_gradle_command`, `background_run_gradle_command`, `run_single_task_and_get_output`, `run_tests_with_gradle`, `run_many_test_tasks_with_gradle`, `background_build_stop`                                                |
| **Information**  | `inspect_gradle_build`        | `lookup_build`, `lookup_build_tasks`, `lookup_build_failures`, `lookup_build_problems`, `lookup_build_console_output`, `lookup_build_tests`, `lookup_latest_builds`, `background_build_list`, `background_build_get_status` |
| **Dependencies** | `inspect_gradle_dependencies` | `get_dependencies`                                                                                                                                                                                                          |
| **Sources**      | `inspect_dependency_sources`  | `search_dependency_sources`, `read_dependency_source_path`                                                                                                                                                                  |
| **Maven**        | `search_maven_artifacts`      | `search_maven_central`, `search_maven_versions`                                                                                                                                                                             |
| **Docs**         | `query_gradle_docs`           | `get_all_gradle_docs_pages`, `get_gradle_docs_page`, `get_gradle_release_notes`, `search_gradle_docs`                                                                                                                       |

---

## 2. Tool Specifications & Argument Mapping

### 2.1 `gradle_execute`

**Description**: The primary tool for managing the Gradle build lifecycle. It can start builds (foreground/background) and stop active background builds.

| Argument              | Type            | Description                                                                 | Mapping / Notes                           |
|:----------------------|:----------------|:----------------------------------------------------------------------------|:------------------------------------------|
| **`commandLine`**     | `List<String>?` | The arguments for `gradlew` (e.g., `[":app:test", "--tests", "MyTest"]`).   | Replaces all run tools.                   |
| **`background`**      | `Boolean`       | If `true`, starts in background and returns `BuildId`. Defaults to `false`. | Replaces `background_run_gradle_command`. |
| **`stopBuildId`**     | `String?`       | The `BuildId` of an active build to stop.                                   | Replaces `background_build_stop`.         |
| `captureTaskOutput`   | `String?`       | Path of a task to extract and return output for exclusively.                | From `run_single_task_and_get_output`.    |
| `projectRoot`         | `String?`       | The directory containing the `gradlew` script.                              |                                           |
| `invocationArguments` | `Object?`       | Optional JVM arguments, environment variables, or system properties.        |                                           |

### 2.2 `inspect_gradle_build`

**Description**: The central tool for retrieving build information, monitoring progress, and diagnosing failures. It acts as a "Dashboard" and "Deep Dive" tool for all builds (active and historical).

| Argument         | Type           | Description                                                                                         | Mapping / Notes                                              |
|:-----------------|:---------------|:----------------------------------------------------------------------------------------------------|:-------------------------------------------------------------|
| **`buildId`**    | `String?`      | The build to inspect. **If omitted, returns the build dashboard** (active builds + recent history). | Replaces `lookup_latest_builds` and `background_build_list`. |
| **`include`**    | `List<String>` | Sections to return: `summary`, `tasks`, `failures`, `problems`, `console`, `tests`.                 | Replaces all `lookup_*` tools.                               |
| **`wait`**       | `Double?`      | Max seconds to wait for an active build to reach a state or finish.                                 | From `background_build_get_status`.                          |
| `waitFor`        | `String?`      | Regex pattern to wait for in the build logs.                                                        | From `background_build_get_status`.                          |
| `waitForTask`    | `String?`      | Task path to wait for completion.                                                                   | From `background_build_get_status`.                          |
| `tasksOptions`   | `Object?`      | `{limit, offset, pathPrefix, details: taskPath}`                                                    | Replaces `lookup_build_tasks`.                               |
| `testsOptions`   | `Object?`      | `{limit, offset, namePrefix, details: {name, index}}`                                               | Replaces `lookup_build_tests`.                               |
| `consoleOptions` | `Object?`      | `{limit, offset, tail}`                                                                             | Replaces `lookup_build_console_output`.                      |

### 2.3 `inspect_gradle_dependencies`

**Description**: Query project dependencies, check for available updates, and view repository configurations.

| Argument          | Type      | Description                                                  | Mapping / Notes                |
|:------------------|:----------|:-------------------------------------------------------------|:-------------------------------|
| **`projectPath`** | `String`  | The Gradle project path (e.g., `:app`). Defaults to root.    | From `get_dependencies`.       |
| `configuration`   | `String?` | Filter by specific configuration (e.g., `runtimeClasspath`). | From `get_dependencies`.       |
| `sourceSet`       | `String?` | Filter by source set (e.g., `test`).                         | From `get_dependencies`.       |
| `checkUpdates`    | `Boolean` | Check against repositories for newer versions.               | From `get_dependencies`.       |
| `onlyDirect`      | `Boolean` | Only show direct dependencies. Defaults to `true`.           | From `get_dependencies`.       |
| `updatesOnly`     | `Boolean` | Only show a summary of available updates.                    | From `get_dependencies`.       |
| `stableOnly`      | `Boolean` | Ignore pre-release versions (alpha, beta, rc, etc.).         | Replaces `stableVersionsOnly`. |
| `versionFilter`   | `String?` | Regex for filtering candidate update versions.               | From `get_dependencies`.       |

### 2.4 `inspect_dependency_sources`

**Description**: Search for symbols or read specific source files from external library dependencies.

| Argument         | Type      | Description                                   | Mapping / Notes                     |
|:-----------------|:----------|:----------------------------------------------|:------------------------------------|
| **`dependency`** | `String`  | The dependency ID (GAV) to search within.     | From `search_dependency_sources`.   |
| `query`          | `String?` | Search query for symbols or file names.       | From `search_dependency_sources`.   |
| `path`           | `String?` | Specific file path within the source to read. | From `read_dependency_source_path`. |

### 2.5 `search_maven_artifacts`

**Description**: Find libraries or view version history on Maven Central.

| Argument    | Type      | Description                                              | Mapping / Notes                   |
|:------------|:----------|:---------------------------------------------------------|:----------------------------------|
| **`query`** | `String`  | The search query or GAV identifier.                      | From `search_maven_central`.      |
| `versions`  | `Boolean` | If `true`, list all available versions for the artifact. | Replaces `search_maven_versions`. |

### 2.6 `query_gradle_docs`

**Description**: Search and read the Gradle User Guide, release notes, and version documentation.

| Argument       | Type      | Description                                         | Mapping / Notes                      |
|:---------------|:----------|:----------------------------------------------------|:-------------------------------------|
| `query`        | `String?` | Search query for the documentation.                 | From `search_gradle_docs`.           |
| `path`         | `String?` | Specific documentation page path to read.           | From `get_gradle_docs_page`.         |
| `version`      | `String?` | Specific Gradle version documentation to target.    | Defaults to project version.         |
| `releaseNotes` | `Boolean` | If `true`, fetch the release notes for the version. | Replaces `get_gradle_release_notes`. |

---

## 3. Skill Refactoring & Workflow Mapping

I will refactor the existing skills and add a new `gradle-dependencies` skill. These skills will adopt the **Progressive Disclosure** pattern: the main `SKILL.md` will focus on common workflows, while complex diagnostics and edge cases will
be moved to `references/`.

### 3.1 `gradle-build` Skill

**Goal**: Handle all build execution and monitoring.

| Workflow                    | Old Tool(s)                                      | New Tool Usage                                                             |
|:----------------------------|:-------------------------------------------------|:---------------------------------------------------------------------------|
| **Run Build**               | `run_gradle_command`                             | `gradle_execute(commandLine=["build"])`                                    |
| **Run Task (Clean Output)** | `run_single_task_and_get_output`                 | `gradle_execute(commandLine=[":app:help"], captureTaskOutput=":app:help")` |
| **Start Background Job**    | `background_run_gradle_command`                  | `gradle_execute(commandLine=["bootRun"], background=true)`                 |
| **Stop Background Job**     | `background_build_stop`                          | `gradle_execute(stopBuildId="BUILD_ID")`                                   |
| **Monitor Progress**        | `background_build_get_status`                    | `inspect_gradle_build(buildId="ID", wait=30, waitFor="Started")`           |
| **Build Dashboard**         | `lookup_latest_builds`                           | `inspect_gradle_build()` (no arguments)                                    |
| **Deep Diagnostic**         | `lookup_build_failures`, `lookup_build_problems` | `inspect_gradle_build(buildId="ID", include=["failures", "problems"])`     |

### 3.2 `gradle-test` Skill

**Goal**: Run and debug tests.

| Workflow               | Old Tool(s)             | New Tool Usage                                                                                 |
|:-----------------------|:------------------------|:-----------------------------------------------------------------------------------------------|
| **Run All Tests**      | `run_gradle_command`    | `gradle_execute(commandLine=["test"])`                                                         |
| **Run Specific Test**  | `run_tests_with_gradle` | `gradle_execute(commandLine=["test", "--tests", "MyTestClass"])`                               |
| **Debug Test Failure** | `lookup_build_tests`    | `inspect_gradle_build(buildId="ID", include=["tests"], testsOptions={details: "MyTestClass"})` |

### 3.3 `gradle-dependencies` (New Skill)

**Goal**: Manage project dependencies and version updates.

| Workflow              | Old Tool(s)                          | New Tool Usage                                          |
|:----------------------|:-------------------------------------|:--------------------------------------------------------|
| **List Dependencies** | `get_dependencies`                   | `inspect_gradle_dependencies(projectPath=":app")`       |
| **Check for Updates** | `get_dependencies(updatesOnly=true)` | `inspect_gradle_dependencies(updatesOnly=true)`         |
| **Find New Libs**     | `search_maven_central`               | `search_maven_artifacts(query="kotlinx-serialization")` |

### 3.4 `gradle-library-sources` (New Skill)

**Goal**: Search and explore the source code of library dependencies.

| Workflow                 | Old Tool(s)                   | New Tool Usage                                                                         |
|:-------------------------|:------------------------------|:---------------------------------------------------------------------------------------|
| **Search Lib Sources**   | `search_dependency_sources`   | `inspect_dependency_sources(dependency="org.junit:junit", query="Assert")`             |
| **Read Lib Source File** | `read_dependency_source_path` | `inspect_dependency_sources(dependency="org.junit", path="src/main/java/Assert.java")` |

### 3.5 `gradle-docs` Skill

**Goal**: Access Gradle help and documentation.

| Workflow                | Old Tool(s)                | New Tool Usage                                          |
|:------------------------|:---------------------------|:--------------------------------------------------------|
| **Search Docs**         | `search_gradle_docs`       | `query_gradle_docs(query="kotlin dsl")`                 |
| **Read Guide Page**     | `get_gradle_docs_page`     | `query_gradle_docs(path="command_line_interface.html")` |
| **Check Release Notes** | `get_gradle_release_notes` | `query_gradle_docs(releaseNotes=true, version="8.6")`   |

---

## 4. Implementation Plan (Small Steps)

### Phase 1: Kotlin Tool Implementation

#### Step 1.1: Common Infrastructure

1. **Define Argument Classes**: In each respective tool file (e.g., `GradleExecutionTools.kt`), define the new consolidated `@Serializable` data classes.
2. **Add Names to `ToolNames.kt`**: Introduce the 6 new tool names while keeping the old ones for a transitional period.

#### Step 1.2: Implement `gradle_execute` (Lifecycle)

1. **Merge Logic**: In `GradleExecutionTools.kt`, implement the `gradle_execute` logic that branch between:
    - `stopBuildId` provided: Locate the active build and call `stop()`.
    - `background` true: Call `GradleProvider.runBuild` and return the `BuildId` immediately.
    - Foreground run: Execute the command and capture output.
2. **Task Output Extraction**: If `captureTaskOutput` is set, implement the regex-based extraction of task-specific logs from the final build output.
3. **Deprecate Old Tools**: Mark `run_gradle_command`, `background_run_gradle_command`, etc., as `@Deprecated`.

#### Step 1.3: Implement `inspect_gradle_build` (Information)

1. **Dashboard Mode**: Implement the logic to return a list of active and recent builds when `buildId` is null.
2. **Wait Logic**: Integrate the `wait`, `waitFor`, and `waitForTask` logic from `BackgroundBuildTools.kt` into the new tool.
3. **Inclusion Filtering**: Implement a system to conditionally build the output string based on the `include` list (summary, tasks, failures, etc.).
4. **Nested Options**: Implement sub-object handling for `tasksOptions`, `testsOptions`, and `consoleOptions`.

#### Step 1.4: Implement Dependency & Documentation Consolidation

1. **`inspect_gradle_dependencies`**: Merge update checking and summary logic into the main dependency query.
2. **`inspect_dependency_sources`**: Combine the search and read functionality into one tool that accepts either a `query` or a `path`.
3. **`search_maven_artifacts`**: Implement the dual-mode tool for artifact search and version listing.
4. **`query_gradle_docs`**: Implement the search-and-read tool, including release notes fetching.

#### Step 1.5: Final Swap & Verification

1. **Remove Old Tools**: After verifying the new tools work, delete the deprecated tool definitions and their constants.
2. **Internal Updates**: Update `DI.kt` and `McpServer.kt` to ensure all tools are correctly registered.
3. **Unit Tests**: Write targeted tests for each new tool's multi-mode behavior.

---

### Phase 2: Skill Refactoring (Progressive Disclosure)

#### Step 2.1: Refactor `gradle-build`

1. **Update `SKILL.md`**: Rewrite to use `gradle_execute` for all runs and `inspect_gradle_build` for status.
2. **Create `references/failure-analysis.md`**: Extract complex failure diagnosis steps into this document.
3. **Create `references/background-monitoring.md`**: Detail patterns for waiting on log messages in background jobs.

#### Step 2.2: Refactor `gradle-test`

1. **Update `SKILL.md`**: Focus on `--tests` CLI usage.
2. **Create `references/test-diagnostics.md`**: Move structured test inspection details here.

#### Step 2.3: Create New Dependency & Source Skills

1. **`gradle-dependencies`**: Focus on version management and repository configuration.
2. **`gradle-library-sources`**: Provide deep workflows for symbol searching and source reading.
3. **Create `references/source-indexing.md`**: Detail how the library source indexing and search work.

#### Step 2.4: Update `gradle-docs`

1. **Update `SKILL.md`**: Simplify to a single search-and-read workflow using `query_gradle_docs`.

---

## 5. Next Steps

1. **Tool Refactoring**: I will begin by consolidating the Kotlin tool implementations in `src/main/kotlin`.
2. **Skill Creation**: I will use the `skill-creator` to package the new and updated skills.
3. **Validation**: Run full project test suite.
