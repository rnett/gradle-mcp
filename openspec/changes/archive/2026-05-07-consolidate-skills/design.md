## Context

The `gradle-mcp` server ships nine skills in `src/main/skills/` that provide expert guidance on using Gradle MCP tools. The skills fall into four clusters based on the MCP tools they instruct:

| Tool cluster                                            | Skills (current)                                                                                  |
|---------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `gradle` + `query_build` + `wait_build` + `gradle_docs` | `running_gradle_builds`, `running_gradle_tests`, `introspecting_gradle_projects`, `gradle_expert` |
| `search_dependency_sources` + `read_dependency_sources` | `searching_dependency_sources`, `researching_gradle_internals`                                    |
| `inspect_dependencies` + `lookup_maven_versions`        | `managing_gradle_dependencies`                                                                    |
| `kotlin_repl`                                           | `interacting_with_project_runtime`, `verifying_compose_ui`                                        |

The first cluster has four skills with significant overlap: shared `query_build` diagnostic patterns (copied across multiple SKILL.md bodies and reference files), overlapping constitutions (absolute paths, foreground preference, tool usage
rules), and a thin meta-skill (`gradle_expert`) that delegates most work to other skills. The second cluster has two skills differentiated only by search scope (`gradleSource: true` vs project dependency scope), using identical tools.

The `install_gradle_skills` tool copies these skill directories into the agent's skill directory. Any file reorganization must preserve the Maven resource path so the installer continues to find skill directories under `src/main/skills/`.

## Goals / Non-Goals

**Goals:**

- Organize skills by MCP tool cluster: one skill per primary tool surface
- Eliminate duplicated `query_build` diagnostic content by extracting a single shared reference
- Dissolve `gradle_expert` by redistributing its content to natural domain homes
- Rename `searching_dependency_sources` to reflect its broader scope (deps, plugins, Gradle internals)
- Preserve all unique workflows, reference material, and constitutional rules from the original nine skills

**Non-Goals:**

- Do NOT modify any MCP tool implementations or signatures
- Do NOT change `interacting_with_project_runtime` or `verifying_compose_ui`
- Do NOT change the `install_gradle_skills` tool's resource discovery mechanism (it scans `src/main/skills/` by directory)
- Do NOT alter the AGENTS.md or OpenSpec documentation (that's a separate concern)
- Do NOT change any Kotlin build logic or dependency configuration

## Decisions

### Decision 1: Organize around tool surfaces, not task domains

The current organization mixes task domains (running builds, running tests, introspecting projects) even when they use identical tools. The new organization uses one skill per primary MCP tool surface:

| Skill                              | Primary tool(s)                                        |
|------------------------------------|--------------------------------------------------------|
| `gradle`                           | `gradle`, `query_build`, `wait_build`, `gradle_docs`   |
| `exploring_dependency_sources`     | `search_dependency_sources`, `read_dependency_sources` |
| `managing_gradle_dependencies`     | `inspect_dependencies`, `lookup_maven_versions`        |
| `interacting_with_project_runtime` | `kotlin_repl`                                          |
| `verifying_compose_ui`             | `kotlin_repl` (Compose-specific)                       |

**Rationale**: Each skill's Constitution and Directives map directly to one tool's invocation patterns, flags, and scope rules. This eliminates the need for cross-reference disclaimers ("Do NOT use for X, use skill Y instead") between
same-tool skills. The description is naturally scoped: "use for ANY `gradle` tool invocation."

**Alternative considered**: Keep builds/tests/introspection split for description precision. Rejected because description precision can be achieved with a well-crafted broad description, and the cost of duplicated constitutions and
diagnostic sections is higher than any routing benefit.

### Decision 2: Extract shared `query_build` diagnostics to a single reference file

Create `gradle/references/query_build_diagnostics.md` merging content from:

- `running_gradle_builds/references/failure_analysis.md` (build failures, problems, tasks, console, scans)
- `running_gradle_tests/references/test_diagnostics.md` (test listing, per-test output, pagination)
- Inline `query_build` sections in `running_gradle_builds/SKILL.md` and `running_gradle_tests/SKILL.md`

**Rationale**: The same `query_build` patterns appear in four skills. Writing them once in a reference file reduces SKILL.md body size, eliminates drift risk, and follows progressive disclosure principles (Phase 3 loading only when
diagnostics are needed).

### Decision 3: Place `gradle_docs` in the `gradle` skill

Official Gradle documentation (`gradle_docs` tool) covers build features, DSL syntax, task configuration, and release compatibility — all concerns of the build engineer using the `gradle` tool. It does not belong in the source-exploration
skill because docs are not source code.

**Alternative considered**: Place `gradle_docs` in `exploring_dependency_sources` as a unified "external knowledge" skill. Rejected because docs are conceptually closer to build execution (you research docs to configure builds) than to
dependency source reading (you read source to understand APIs).

### Decision 4: Split `internal_research_guidelines.md` across two destinations

The `gradle_docs` sections (userguide, DSL, samples, javadocs, best practices discovery) move to `gradle/references/gradle_docs_research.md`. The source-search sections (gradleSource declaration search, plugin exploration, path syntax) move
to a new `exploring_dependency_sources/references/internal_source_research.md`.

**Rationale**: This file was the only artifact bridging two tool surfaces. Splitting keeps references colocated with their parent skill.

## Risks / Trade-offs

| Risk                                                                                                                                             | Mitigation                                                                                                                                                                                                                            |
|--------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Broad `gradle` description may route less precisely than separate specialized descriptions                                                       | Description explicitly lists all trigger domains (builds, tests, introspection, modules, docs). Negative triggers exclude dependency management and source exploration. Monitor after deployment.                                     |
| `install_gradle_skills` may fail if it hardcodes skill names rather than scanning directories                                                    | Verify installer implementation before finalizing. If it enumerates names, update the enumeration. Per the installer's SKILL.md, it "replaces existing skills from this MCP server in the target directory" — likely directory-based. |
| Renaming `searching_dependency_sources` → `exploring_dependency_sources` may orphan stale references in agent tool descriptions or documentation | The `updateToolsList` Gradle task regenerates tool documentation from source. Run it after the rename.                                                                                                                                |
| Reference file reorganization may break relative `{baseDir}` links in SKILL.md bodies                                                            | All links already use `{baseDir}/references/` pattern. Verify all links resolve after directory moves.                                                                                                                                

## Migration Plan

1. Create new `gradle/` and `exploring_dependency_sources/` directories with full content
2. Verify all reference files are present and links resolve
3. Run `./gradlew :updateToolsList` to refresh tool documentation
4. Run `./gradlew build` to verify no resource packaging issues
5. Delete obsolete skill directories
6. No rollback strategy needed — this is a documentation-only change. Git revert suffices.

## Open Questions

- Should the `gradle` skill include `inspect_dependencies` diagnostic patterns (dependencyInsight, outgoingVariants, resolvableConfigurations) or should those stay in `managing_gradle_dependencies`? Leaning toward keeping them in
  `managing_gradle_dependencies` since they're dependency-graph concerns, but `dependencyInsight` is invoked via the `gradle` tool.
