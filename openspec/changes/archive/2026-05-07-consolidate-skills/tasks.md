## 1. Create shared query_build diagnostics reference

- [x] 1.1 Merge `running_gradle_builds/references/failure_analysis.md` and `running_gradle_tests/references/test_diagnostics.md` and related inline sections into a single `gradle/references/query_build_diagnostics.md` covering: DASHBOARD,
  SUMMARY, FAILURES, PROBLEMS, TASKS, TESTS, CONSOLE, and PROGRESS with JSON examples

## 2. Create the new `gradle` skill

- [x] 2.1 Create `src/main/skills/gradle/` directory and `SKILL.md` with YAML frontmatter (name: `gradle`, description covering builds, tests, introspection, module creation, docs research, diagnostics; negative triggers for dependency
  management and source exploration)
- [x] 2.2 Write the Constitution section: merge and deduplicate rules from `running_gradle_builds`, `running_gradle_tests`, `introspecting_gradle_projects`, and `gradle_expert`
- [x] 2.3 Write the Directives section: foreground vs background, task/selector path syntax, `captureTaskOutput` usage, `gradle_docs` tag syntax, `--tests` filtering patterns, idiomatic DSL patterns (lazy APIs, version catalogs)
- [x] 2.4 Write Workflows section: (a) Running builds, (b) Running tests, (c) Introspecting project structure, (d) Creating a module, (e) Performance audit, (f) Documentation research
- [x] 2.5 Write When to Use section: consolidated trigger list from all four source skills
- [x] 2.6 Write Examples section: 6-8 curated examples covering builds, tests, introspection, module creation, docs research, and diagnostics — each showing tool invocation with reasoning
- [x] 2.7 Link to `{baseDir}/references/query_build_diagnostics.md` for detailed diagnostic patterns

## 3. Migrate reference files for the `gradle` skill

- [x] 3.1 Move `running_gradle_builds/references/background_monitoring.md` to `gradle/references/background_monitoring.md`
- [x] 3.2 Move `introspecting_gradle_projects/references/diagnostic_tasks.md` to `gradle/references/diagnostic_tasks.md` and update any internal path references
- [x] 3.3 Move `gradle_expert/references/best_practices.md` to `gradle/references/best_practices.md`
- [x] 3.4 Move `gradle_expert/references/common_build_patterns.md` to `gradle/references/common_build_patterns.md`
- [x] 3.5 Split `gradle_expert/references/internal_research_guidelines.md`: extract `gradle_docs` sections into `gradle/references/gradle_docs_research.md`

## 4. Rename and expand `searching_dependency_sources` to `exploring_dependency_sources`

- [x] 4.1 Rename directory `src/main/skills/searching_dependency_sources/` to `src/main/skills/exploring_dependency_sources/`
- [x] 4.2 Update SKILL.md YAML frontmatter: `name: exploring_dependency_sources`, broader description covering project deps, plugins, AND Gradle internals
- [x] 4.3 Absorb `gradleSource: true` search patterns and `fqn:` glob wildcards from `researching_gradle_internals`
- [x] 4.4 Absorb plugin source exploration patterns from `gradle_expert` (`sourceSetPath=":buildscript"`, `configurationPath=":buildscript:classpath"`)
- [x] 4.5 Create `exploring_dependency_sources/references/internal_source_research.md` from the source-search half of the split `internal_research_guidelines.md`

## 5. Enrich `managing_gradle_dependencies`

- [x] 5.1 Add "Adding a Dependency" workflow to SKILL.md: search Maven Central → update version catalog → apply to `build.gradle.kts` → verify
- [x] 5.2 Add plugin dependency verification workflow via `sourceSetPath=":buildscript"`

## 6. Delete obsolete skill directories

- [x] 6.1 Delete `src/main/skills/gradle_expert/`
- [x] 6.2 Delete `src/main/skills/running_gradle_tests/`
- [x] 6.3 Delete `src/main/skills/introspecting_gradle_projects/`
- [x] 6.4 Delete `src/main/skills/researching_gradle_internals/`
- [x] 6.5 Delete `src/main/skills/running_gradle_builds/`

## 7. Update tool documentation and verify

- [x] 7.1 Run `./gradlew :updateToolsList` to regenerate tool documentation
- [x] 7.2 Run `./gradlew build` to verify no packaging issues with the reorganized skill directories
- [x] 7.3 Verify all `{baseDir}/references/` links in new and modified SKILL.md files resolve correctly by checking file existence
