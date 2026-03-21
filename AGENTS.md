# Gradle MCP Project Memory

This repository contains a Model Context Protocol (MCP) server written in Kotlin, designed to provide LLMs with deep, programmatic access to Gradle build systems. It serves as both a tool provider and a laboratory for agentic Gradle
workflows.

## Primacy Zone: Fundamental Mandates

1. **Security & Secrets**: NEVER log, print, or commit API keys, secrets, or .env contents.
2. **Source Control**: ALWAYS add changes to Git for persistence, but NEVER create commits or push unless explicitly instructed.
3. **Tool Preference**: ALWAYS prefer using the Gradle MCP tools (`gradle`, `inspect_build`, etc.) over raw shell execution of `./gradlew` or generic shell commands.
4. **Verification**: NO change is complete without passing relevant tests and the final `test` task. Only run `check` or `integrationTest` for changes that may influence them.
5. **Tool Metadata**: After modifying any tool descriptions or metadata, or making structural changes that might affect tool discovery, you MUST run `./gradlew :updateToolsList` to ensure consistency. This is required even if no `@McpTool`
   annotations were directly changed.
6. **Agent Documentation**: This is an MCP server and set of tools for agents to use. If behavior or features aren't documented in the tool descriptions AND skills, they might as well not exist. Ensure they are documented well enough in
   both places for agents to use them.

---

## WHAT (Architecture & Terminology)

### Core Components

- **MCP Server**: The main entry point using the Model Context Protocol Kotlin SDK.
- **Gradle Provider**: The internal engine that orchestrates Gradle Tooling API calls and manages background builds.
- **Repl Worker**: A separate process (`repl-worker` module) that executes Kotlin snippets for prototyping and UI verification.
- **Skills**: Specialized agentic workflows stored in `./skills/`. These are "agentic documentation" that guide Gemini on how to solve complex Gradle tasks. These skills are designed to be generic and portable, intended for installation by
  any user of this Gradle MCP server. They must avoid project-specific hardcoding and focus on idiomatic Gradle usage across diverse environments.

### Key Terms

- **BuildId**: A unique UUID used to track and monitor background builds.
- **Task Output Capturing**: A mechanism to isolate the console output of a specific task, reducing context noise.
- **ToolNames**: The source of truth for all MCP tool identifiers (see `src/main/kotlin/dev/rnett/gradle/mcp/tools/ToolNames.kt`).

---

## WHY (Architectural Rationale)

### Kotlin & Koin

- **Why Kotlin?**: To leverage Gradle's native type system and the rich ecosystem of JVM-based build tools.
- **Why Isolated Koin?**: To prevent global state leakage between tool calls and ensure deterministic behavior in the MCP server's request-response lifecycle.

### Testing & Assertions

- **Why Power Assert?**: It eliminates the need for complex assertion libraries by providing rich, contextual failure messages from simple `assert` calls.
- **Why Class-Level Test Resources?**: Creating Gradle projects and daemon environments is expensive; we reuse them across tests to maintain a fast feedback loop.
- **Rule**: NEVER use reflection hacks for tests. ALWAYS close and clean up any resources or services they create.
- **Integration test split**: **slow** integration tests, or other slow tests, may be placed in `integrationTest`. This is a PERFORMANCE concern, NOT an architectural one. The integration tests are also ran sequentially within a JVM, and
  use multiple JVMs for parallelization isntead of threads.
- **Progress Logging**: Use `ProgressReporter.Companion.PRINTLN` for in tests instead of NONE.

### MCP Design

- **Why Text over JSON?**: LLMs process natural language better than raw JSON. Tools should return structured, human-readable text (often Markdown) to maximize reasoning.
- **Why Tooling API?**: It is the official, supported way to interact with Gradle programmatically, providing better stability than parsing CLI output.
- **MCPs vs Skills**: While using our skills in `./skills` should be encouraged as the primary endpoint, the MCP must work without the tools. This means that the MCP tool descriptions must contain enough information to use the without
  relying on the skills.
- **Standardized Ambiguity Reporting**: When implementing prefix matching in lookup tools, always use a standardized "exact match -> unique prefix match -> ambiguous prefix match" flow. Ambiguity reports should return a distinct, sorted
  list of up to 10 matching names/paths with a clear "Please provide a full..." footer. This ensures UX consistency and prevents LLM context bloat.

---

## HOW (Operational Execution)

- **Prefix Stability**: This file (`AGENTS.md`) is designed to be at the start of the context. Maintain its structure to maximize context caching hits.
- **Instruction Density**: Focus on non-obvious constraints. If it can be inferred from the file structure, don't add it here.
- **Caching**: This workspace uses heavy caching for Gradle sources. Use `inspect_dependencies` and `search_dependency_sources` with `fresh = true` only when dependencies change.
    - **Flexible Path Abstractions**: When representing filesystem structures for caches (like `SourcesDir`), prefer interfaces with flexible implementations (e.g., `MergedSourcesDir`, `SingleDependencySourcesDir`) over rigid data classes.
      This allows pointing directly to global cache directories and indices, eliminating the need for project-level "wrapper" directories, symlink overhead, and marker files for isolated targeted searches.
    - **Multi-Layer Filtering**: When implementing filtering across multiple layers (e.g., init script vs service), document discrepancies in filtering capabilities (e.g., G:A:V vs G:A:V:Variant). Ensure the final layer provides precise
      verification to safely handle over-fetching from limited earlier layers while maintaining correctness and performance.
    - **Cache Invalidation**: When implementing re-extraction or re-indexing logic with `forceDownload`, explicitly propagate the flag to all underlying services (like `IndexService`) to ensure stale caches are invalidated.
    - **Expensive Cached Operations**: In `withSources` (and similar cached operations), perform expensive external calls (like Gradle `resolve()`) exactly once under an exclusive lock, and only after checking a shared lock for a fresh
      cache.
    - **Lock File Naming**: Include the group name or a hash of the full path in global lock file names for shared resources to prevent collisions across different organizations.
- **Parallel Source Processing**: Source extraction and indexing are performed in parallel using Kotlin `Flow` and `flatMapMerge`. This significantly improves performance but requires careful concurrency management (limiting global IO
  tasks).
    - **Guideline**: Prefer `flatMapMerge` (via `parallelMap`/`parallelForEach` utilities) for concurrency control over manual `Semaphore` management when working with Kotlin Flows. This simplifies code and leverages built-in coroutine
      orchestration.
    - **Naming**: Explicitly name non-deterministic parallelism utilities (e.g., `unorderedParallelMap`) to signal that input order is not preserved.
  - **Flow Draining**: Always ensure that indexing operations consume the entire file flow, even if the index is already up-to-date, when using a `Channel` based extraction pipeline to prevent deadlocks.
- **Gradle Source Exploration**: On some machines, the Gradle build tool project is checked out in `./managing-gradle-builds-tool`. Use it as a source of knowledge for Gradle's internal APIs, but be careful not to include it in broad
  search/build commands unless intended. See [gradle-sources-overview.md](./openspec/docs/gradle-sources-overview.md).

### Lucene & Search Conventions

- **Field Constants**: For Lucene-based search providers (e.g., `DeclarationSearch`), field names MUST be extracted to a nested `Fields` object. This ensures consistency and prevents typos across indexing and searching logic.
- **Lucene 10+ API**: Use `writer.docStats.numDocs` to retrieve the current document count from an `IndexWriter`.
- **Metadata Caching**: Always cache expensive metadata (like document counts) in a lightweight file (e.g., `.count`) within the index directory. This enables instantaneous preparation phases and provides immediate progress reporting
  feedback.
- **Object Pooling**: For heavy, non-thread-safe objects like `TreeSitterDeclarationExtractor`, prefer a `ConcurrentLinkedQueue`-based pool over `ThreadLocal` when using Kotlin Coroutines. This ensures better resource management and
  predictability across diverse threading environments.
- **Regex Search**: `DeclarationSearch` supports full string regex queries on the FQN field when the query is wrapped in `/` (e.g., `/.*MyClass/`). This should be preferred for complex, precise symbol discovery.
- **Search Error Handling**: `SourcesService.search` and `IndexService.search` MUST return a `SearchResponse` with an `error` string instead of throwing an `IllegalStateException` when an index is missing. This prevents unexpected crashes
  in tool handlers and allows for graceful error reporting to the user/LLM.
- **Targeted Indexing**: `SourcesService.downloadAllSources` (and related methods) require an explicit `providerToIndex: SearchProvider` to be passed when `index = true`. This ensures that indexing is targeted and efficient. Callers must
  specify which provider's index they intend to use.

### Build & Test Commands

- **Build All**: `./gradlew build`
- **Run Fast Tests**: `./gradlew test` - usually good enough for most changes
- **Run All Tests**: `./gradlew test integrationTest`
- **Quality Check**: `./gradlew check` (Runs all verification tasks including linting and tests)
- **Update Tools**: `./gradlew :updateToolsList` (Mandatory after metadata changes)

### Research & Problem Solving

- **Research First**: ALWAYS research the correct way to implement a feature or use a library before attempting implementation. Do not rely on trial-and-error for complex APIs like Kotlin Scripting.
- **Rubber Ducking**: If stuck after a few tries, stop and think about the issues before proceeding. Rubber duck to yourself and document your findings.

### Code Style & Conventions

- **Kotlin 2.3+ Standard Library**: ALWAYS prefer Kotlin's native types over Java-specific ones. Refer to the `kotlin_reference` skill for full details.
    - **Atomics**: Use `kotlin.concurrent.atomics.*` (`AtomicInt`, `AtomicLong`, `AtomicBoolean`, `AtomicReference`). Note: These require `@OptIn(ExperimentalAtomicApi::class)`.
    - **Time**: Use `kotlin.time.*` (`Instant`, `Clock`, `Duration`). `Clock.System.now()` is the standard for current time.
  - **UUID**: Use `kotlin.uuid.Uuid` for UUID operations (e.g., `Uuid.parse()`, `Uuid.random()`). Note: This requires `@OptIn(ExperimentalUuidApi::class)`.
    - **Coroutines**: We use Coroutines and Flows extensively. This means AVOID SYNCHRONIZATION whenever possible as it pins and blocks the underlying thread.
  - **Multi-Lock Acquisition**: Avoid implementing utilities that acquire multiple file locks simultaneously (`withLocks`) in a coroutine environment, especially within tests. This reduces the risk of complex deadlocks and
    `UncompletedCoroutinesError` in `runTest` while simplifying concurrency management.
- **Test Naming**: Always name tests using descriptive names in English, wrapped in backticks (e.g., `` `verify build status wait` ``).
- **Concurrency**: ALWAYS use `runTest` for suspending tests, NEVER `runBlocking`.
- **Dependency Catalog**: ALWAYS put dependencies in `gradle/libs.versions.toml`. Every version ref MUST have a corresponding entry for automated updates.
- **Progress Reporting**: ALWAYS send progress commands/notifications (`ProgressReporter`) when implementing long-running operations or tool handlers.
  - **Initial Feedback**: Always report an initial "Starting..." or "Preparing..." progress status (0.0) before invoking a long-running operation (like a Gradle build through the Tooling API) to prevent the user from perceiving the tool
    as "stuck" during connection/startup delays.
  - **Frequency**: Do NOT artificially limit reports (e.g., `if count % 100`) as limiting is applied at the top level. However, prioritize overall tool UX; for very fast operations where progress reporting is more noise/overhead than value,
    it is acceptable to omit it. Progress reporting does not have to be perfect - some slight or temporary inaccuracies or concurrency issues are OK - focus on the overall UX.

### Skill Development Workflow

Skills in this project guide other agents. They will be installed in users' skill directories as standalone skills. Follow the "Expert Tone" and "Progressive Disclosure" principles.

1. **Scope**: "Update skills" directives ALWAYS refer to skills in `./skills/`, NEVER global skills.
2. **Sync**: When changing a tool, you MUST update all referencing skills in `./skills/` and other dependent tools.
3. **Create**: New directory in `skills/` with `SKILL.md`.
4. **Refine**: Move deep-dives to `references/`.
5. **Register**: Update `docs/skills.md` with the new skill details.
6. **Reference**: Use relative links to living files to avoid "instruction rot."  But you CAN NOT link to library sources from skills, and double check whether cross skill links will work before adding them.
7. **MCP Tooling:** Skills are largely focused around using this server's MCP tools. The skills in a given commit should work with the tools in a given commit - ensure they are consistent. They may not match the names used by the gradle MCP
   server in your current session - that's OK.

---

## Concurrency and Testing Patterns

### Context parameters

- **Mockk:** To get mockk to work well with context parameters, you need to do something like:

```kotlin
context(any<ContextParamAType>(), any<ContextParamBType>()) {
    myFunction(any(), any<ResolveOverloadParamType>())
}
```

Mock in general needs the specific type args for the `any()` calls to resolve overloads.

### Error Handling & Propagation

- **Fail Fast**: ALWAYS propagate exceptions in indexing, extraction, and search operations. NEVER swallow errors silently. "Well-documented failures" are preferred over silent partial successes.
- **Extraction Tests**: When writing tests for source extraction, avoid using empty or invalid ZIP files. Since extraction failures are no longer swallowed, these will now cause `ZipException` and fail the test.

### Progress Tracking

- **Thread Safety**: Progress trackers (e.g., `BuildProgressTracker`) handle updates from multiple Gradle listener threads. It's OK if progress is not perfectly consistent as long as it is eventually consistent. Avoid heavy synchronization.
- **Reporting Frequency**: Do NOT artificially limit report frequency (e.g., `if count % 100`) in trackers or producers; throttled delivery is handled authoritatively at the top level.
- **Parallel Operations Strategy**: When designing progress reporting for complex parallel operations, prioritize stable activity messaging and independent phase ranges over high-resolution, jittery percentages. Stable task-based messaging
  and sequential phase progression build significantly more user trust than flickering percentages.
- **Merging Progress**: Base merging progress across multiple search providers on the total document count across all providers rather than the number of providers themselves. Provider-based progress is too low-resolution and causes
  significant jumps if document distribution is uneven across index types.
- **State Consolidation**: Prefer consolidating sub-task or granular progress into a single state object within the tracker rather than managing multiple independent variables.
- **Internal Gradle Progress**: For long-running Gradle tasks in init scripts, explicitly report progress during slow internal operations (e.g., dependency resolution) using the format
  `[gradle-mcp] [PROGRESS] [CATEGORY]: CURRENT/TOTAL: MESSAGE`.
  - **Categories**: Use descriptive categories (e.g., `RESOLUTION`, `VERSION_CHECK`) to provide context about the current phase. These are automatically incorporated into the formatted progress message by the `BuildProgressTracker`.
  - **Filtering Output**: When filtering dependencies in Gradle init scripts, distinguish between "skipped by filter" and "up-to-date" in the output format to prevent tools from reporting a false sense of security.
- **Job Management**: When managing background collection jobs (e.g., in `GradleBuildLookupTools`), always use `job.cancelAndJoin()` instead of just `cancel()` to ensure clean termination before proceeding.

### Async Log Processing

- **Completion Signaling**: When implementing asynchronous log processing using a `Channel`, always use a `CompletableDeferred` to signal completion of the processing loop. This prevents race conditions where tools return before all logs
  are buffered and ensures tests using `runTest` do not hang indefinitely waiting for the channel coroutine.

### Testing Asynchronous Events

- **Deterministic Sync**: Avoid `delay()` or `Dispatchers.Unconfined` for synchronizing tests with background progress. Instead, use `CompletableDeferred<Unit>` as a "signal" that can be completed by the tracker and awaited by the test.
- **Test Synchronization**: Use `backgroundScope` in `runTest` when creating objects that launch long-lived background coroutines (like `RunningBuild`). This allows the test to complete normally without waiting for infrastructure coroutines
  that stay active for the object's lifetime.
- **Test Hooks**: It is acceptable to add internal "onProgressFinished" or similar callback hooks to trackers specifically for test synchronization.

---

## Resource Index

- **Internal Documentation**: [openspec/docs/](./openspec/docs/)
- **Tool Definitions**: [ToolNames.kt](src/main/kotlin/dev/rnett/gradle/mcp/tools/ToolNames.kt)
- **User/Human Facing Skill Docs**: [docs/skills.md](./docs/skills.md)
