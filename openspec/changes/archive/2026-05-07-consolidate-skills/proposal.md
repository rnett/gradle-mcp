## Why

Nine MCP skills shipped with this server cover overlapping domains, share duplicated `query_build` diagnostic workflows, and include a thin meta-skill (`gradle_expert`) whose content is better distributed to natural domain homes.
Consolidating reduces maintenance burden, eliminates ~400 lines of duplicated text, improves routing precision by removing semantic overlaps, and produces a cleaner skill surface for the `install_gradle_skills` installer.

## What Changes

- **Merge `running_gradle_builds` + `running_gradle_tests` + `introspecting_gradle_projects` + `gradle_expert`** into a single `gradle` skill. This skill covers all `gradle` and `query_build` tool usage: builds, tests, project
  introspection, module creation, performance audits, and `gradle_docs` documentation research. **BREAKING**: removes four existing skill directories.
- **Rename `searching_dependency_sources` to `exploring_dependency_sources`** and absorb source-search content from `researching_gradle_internals` (`gradleSource: true` patterns) and `gradle_expert` (plugin source exploration). **BREAKING
  **: removes `researching_gradle_internals` directory.
- **Enrich `managing_gradle_dependencies`** with the "adding a dependency" and version catalog workflows from `gradle_expert`.
- **Extract shared `query_build` diagnostics** into a single reference file (`gradle/references/query_build_diagnostics.md`) merged from three overlapping reference files.
- **Remove `gradle_expert` references**: `internal_research_guidelines.md` split between `gradle` and `exploring_dependency_sources`; `best_practices.md` and `common_build_patterns.md` moved to `gradle/references/`.
- **Keep `interacting_with_project_runtime` and `verifying_compose_ui`** unchanged — no overlap with the `gradle` tool surface.

## Capabilities

### New Capabilities

- `gradle-skill`: Unified skill for all `gradle` tool operations — executing builds and tests, introspecting project structure and tasks, running diagnostic tasks (`projects`, `tasks`, `help`, `properties`, `dependencyInsight`), creating
  modules, auditing performance, and researching official Gradle documentation via `gradle_docs`.

### Modified Capabilities

- `exploring-dependency-sources` (was `searching-dependency-sources` in the skill directory, no corresponding OpenSpec spec): Renamed and expanded to cover Gradle internals source code (`gradleSource: true`) and plugin source exploration,
  in addition to project dependency source search/read.

- `managing-gradle-dependencies` (was `managing_gradle_dependencies` in the skill directory): Expanded with dependency addition workflow (Maven Central discovery → version catalog update → `build.gradle.kts` application → verification).

## Impact

- **Affected directories**: `src/main/skills/gradle_expert/`, `src/main/skills/running_gradle_tests/`, `src/main/skills/introspecting_gradle_projects/`, `src/main/skills/researching_gradle_internals/`,
  `src/main/skills/running_gradle_builds/` removed. `src/main/skills/searching_dependency_sources/` renamed to `exploring_dependency_sources/`.
- **New directory**: `src/main/skills/gradle/` with consolidated SKILL.md and references.
- **Reference files**: 8 → ~7; three diagnostic references merged into one. `internal_research_guidelines.md` split across two skills.
- **`install_gradle_skills` tool**: Installer logic may need updating if it indexes by skill name (the set of shipped skill names changes).
- **No tool changes**: All underlying MCP tools (`gradle`, `query_build`, `wait_build`, `search_dependency_sources`, etc.) remain unchanged.
