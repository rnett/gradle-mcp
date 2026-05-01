## Context

The Gradle MCP server exposes several tools for build execution, dependency inspection, source code reading, and documentation querying. AI agents select tools based on their `@Description` annotations and parameter descriptions. Current
descriptions have several issues:

1. **`gradleSource` parameter** on `searchDependencySources` and `readDependencySources` is described too briefly and named ambiguously — agents interpret it as a general "use Gradle" flag rather than "read Gradle's own source code."
2. **`gradle` tool** description says "ALWAYS use instead of raw shell `./gradlew`" but lacks discriminative language that contrasts it with `gradleOwnSource`-based tools.
3. **`projectRoot` parameter** description is ambiguous about when auto-detection works vs. when explicit specification is required.
4. **No cross-references** between tools that serve related but distinct purposes (e.g., `gradle` vs. `query_build` for test results).

All changes are purely descriptive — no API, parameter types, or behavior changes, except for renaming `gradleSource` to `gradleOwnSource`.

## Goals / Non-Goals

**Goals:**

- Reduce agent misuse of `gradleOwnSource: true` on source tools when the agent should use the `gradle` tool
- Increase agent usage of the `gradle` tool as the default for build execution
- Eliminate unnecessary `projectRoot` specification by clarifying auto-detection behavior
- Add cross-references between related tools to guide agents toward the correct tool for each scenario
- Follow the existing `skill-and-tool-descriptions` spec patterns (gerund headers, positive/negative triggers, authoritative language)

**Non-Goals:**

- No changes to tool names, parameter types, or runtime behavior (except renaming `gradleSource` to `gradleOwnSource`)
- No new tools or parameters
- No changes to the `kotlin_repl`, `lookup_maven_versions`, `install_gradle_skills`, or `gradle_docs` tools (unless cross-references are added)
- No changes to skill descriptions (only tool descriptions)

## Decisions

### Decision 1: Rename `gradleSource` to `gradleOwnSource`

- **Approach**: Rename the `gradleSource` boolean parameter to `gradleOwnSource` on both `searchDependencySources` and `readDependencySources`. Update all references in tool descriptions, parameter descriptions, and documentation.
- **Rationale**: The name `gradleSource` is ambiguous — agents interpret it as a general "use Gradle" flag rather than "read Gradle's own source code." `gradleOwnSource` clearly communicates the intent. Since the only consumers are AI
  agents, there is no backwards compatibility concern.
- **Alternative considered**: Renaming to `readGradleInternals` — rejected because "internals" is not the right framing; Gradle's source code is not "internal" in the sense of private implementation details.

### Decision 2: Elevate `gradle` tool with authoritative "default" language

- **Approach**: Add "STRONGLY PREFERRED for all Gradle task execution" to the header. Add a "When NOT to use" section that points to `query_build` for post-build diagnostics and `gradleOwnSource` tools for reading Gradle source code.
- **Rationale**: The tool needs to be positioned as the default, not just an alternative to shell. Explicit negative guidance prevents overuse (e.g., using `gradle` to read build output when `query_build` is better).
- **Alternative considered**: Adding a separate "default tool" hint in the component description — rejected as less actionable than inline tool description changes.

### Decision 3: Simplify `projectRoot` description

- **Approach**: Rewrite to: "Absolute path to the Gradle project root. Auto-detected from MCP roots when only one project is open; specify explicitly for multi-root workspaces or when auto-detection fails."
- **Rationale**: The current description ("Auto-detected from MCP roots or GRADLE_MCP_PROJECT_ROOT when present, must be specified otherwise (usually)") is confusing — "usually" contradicts "auto-detected." The new version clearly states
  when it's needed vs. not.
- **Alternative considered**: Removing `projectRoot` from default values entirely — rejected as too disruptive; the DEFAULT value (null → auto-detect) is correct.

### Decision 4: Add cross-references between tools

- **Approach**: Add brief cross-reference notes in tool descriptions:
    - `gradle` tool: "For post-build diagnostics, test results, and task output, use `query_build`."
    - `query_build`: Already references `gradle` for execution — keep as-is.
    - `searchDependencySources`/`readDependencySources`: Add "To run Gradle tasks or builds, use the `gradle` tool."
- **Rationale**: Cross-references help agents navigate the tool landscape without guessing.

## Risks / Trade-offs

- **[Risk] Description bloat**: Adding too much text to descriptions may cause agents to skip reading them. → **Mitigation**: Keep additions concise (1-2 sentences per change). Follow the existing spec's guidance on description length.
- **[Risk] Over-correction**: Agents may stop using `gradleOwnSource: true` even when it's appropriate. → **Mitigation**: Keep positive triggers ("Use for reading Gradle's own source code") alongside negative triggers.
- **[Risk] Stale cross-references**: If tool names change, cross-references become misleading. → **Mitigation**: Use `ToolNames` constants in code (already done); cross-references in descriptions use the tool name string which would need
  manual updates.
