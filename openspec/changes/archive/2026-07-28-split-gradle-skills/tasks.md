# Tasks: split-gradle-skills

## Phase 1: Create gradle-build-authoring skill skeleton

- [ ] **Task 1.1**: Create directory `src/main/skills/gradle-build-authoring/` and `src/main/skills/gradle-build-authoring/references/`.
- [ ] **Task 1.2**: Create `src/main/skills/gradle-build-authoring/SKILL.md` with full YAML frontmatter (name: gradle-build-authoring, description with capability + positive triggers + negative triggers, license, metadata version "1.0"), title "Authoritative Gradle Build Authoring & Build Logic Engineering", and section stubs (Constitution, Directives, Workflows, When to Use, Examples, Resources).

## Phase 2: Migrate build-authoring content from gradle/SKILL.md

- [ ] **Task 2.1**: Migrate Constitution rules 11-15 (Kotlin DSL, lazy APIs, version catalogs, existing conventions, safe navigation) from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md`. Add 3 new rules: "NEVER use allprojects/subprojects; use convention plugins", "NEVER access Project object inside task actions", "NEVER resolve configurations during configuration phase".
- [ ] **Task 2.2**: Migrate the "Idiomatic DSL Patterns" directive section from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md` Directives and expand.
- [ ] **Task 2.3**: Migrate "Creating a New Module" workflow from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md` Workflows. Expand with convention plugin application.
- [ ] **Task 2.4**: Migrate "Performance Audit" workflow from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md` Workflows. Expand with build scan step.
- [ ] **Task 2.5**: Migrate "Build Logic Refactoring" and the "New Module Creation" and "Performance Troubleshooting" When to Use entries from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md`.
- [ ] **Task 2.6**: Migrate the "Create a new sub-project module" example from `gradle/SKILL.md` to `gradle-build-authoring/SKILL.md` Examples.
- [ ] **Task 2.7**: Migrate resource links for `best-practices/_index.md` and `common_build_patterns.md` to `gradle-build-authoring/SKILL.md` Resources with corrected paths.

## Phase 3: Clean up gradle/SKILL.md

- [ ] **Task 3.1**: Remove migrated Constitution rules (11-15) from `gradle/SKILL.md`. Keep rules 1-10 plus `:properties --property` and `gradle_docs` rules.
- [ ] **Task 3.2**: Remove "Idiomatic DSL Patterns" section entirely from `gradle/SKILL.md`.
- [ ] **Task 3.3**: Remove "Creating a New Module" and "Performance Audit" workflows from `gradle/SKILL.md`.
- [ ] **Task 3.4**: Remove migrated When to Use entries from `gradle/SKILL.md`.
- [ ] **Task 3.5**: Remove migrated example from `gradle/SKILL.md`.
- [ ] **Task 3.6**: Remove `best-practices/_index.md` and `common_build_patterns.md` from `gradle/SKILL.md` Resources.
- [ ] **Task 3.7**: Add "Build Authoring" cross-reference section after Constitution in `gradle/SKILL.md` directing to `gradle-build-authoring`.
- [ ] **Task 3.8**: Add "Build Script Changes" negative trigger to `gradle/SKILL.md` When to Use.
- [ ] **Task 3.9**: Update `gradle/SKILL.md` frontmatter: revise description to remove "creating modules" and "performance audits"; add negative trigger for `gradle-build-authoring`; bump version to "5.0".

## Phase 4: Author 10 new gap-filling reference documents

- [ ] **Task 4.1**: Create `gradle-build-authoring/references/version-catalogs.md` - Full `libs.versions.toml` example with versions, libraries, bundles, plugins sections; type-safe accessor usage; multi-catalog setups.
- [ ] **Task 4.2**: Create `gradle-build-authoring/references/testing-configuration.md` - JUnit 5 platform setup, test logging, task customization, KMP targeting, test fixtures.
- [ ] **Task 4.3**: Create `gradle-build-authoring/references/ci-cd-builds.md` - Daemon management, `--scan`, parallel execution, caching, environment isolation, GitHub Actions patterns.
- [ ] **Task 4.4**: Create `gradle-build-authoring/references/dependency-locking.md` - `dependencyLocking {}`, lockfile generation, CI verification.
- [ ] **Task 4.5**: Create `gradle-build-authoring/references/worker-api.md` - `WorkerExecutor`, isolation modes, parallel work patterns.
- [ ] **Task 4.6**: Create `gradle-build-authoring/references/jdk-toolchains.md` - Toolchain configuration, auto-provisioning, foojay resolver.
- [ ] **Task 4.7**: Create `gradle-build-authoring/references/build-scans.md` - Develocity plugin, publishing config, CI integration, interpretation.
- [ ] **Task 4.8**: Create `gradle-build-authoring/references/continuous-builds.md` - `--continuous`, `--watch-fs`, use cases, limitations.
- [ ] **Task 4.9**: Create `gradle-build-authoring/references/kotlin-compiler-options.md` - `compilerOptions {}`, `jvmTarget`, `freeCompilerArgs`, KMP patterns.
- [ ] **Task 4.10**: Create `gradle-build-authoring/references/artifact-publishing.md` - maven-publish, publications, signing, POM customization.

## Phase 5: Expand common_build_patterns.md and move best-practices

- [ ] **Task 5.1**: Add "Build-Logic Composite Build Setup" section to `gradle/references/common_build_patterns.md`: `includeBuild("build-logic")`, precompiled script plugins, type-safe project accessors.
- [ ] **Task 5.2**: Change `GenerateBestPracticesDoc.kt` output path from `gradle/references/best-practices/` to `gradle-build-authoring/references/best-practices/`. Update `writePages()` function to accept new output directory. Update any tests that hardcode the output path.
- [ ] **Task 5.3**: Run generator to produce best-practices in the new location.
- [ ] **Task 5.4**: Remove stale `gradle/references/best-practices/` directory.

## Phase 6: Complete gradle-build-authoring/SKILL.md body

- [ ] **Task 6.1**: Write full Constitution section with all migrated + new rules.
- [ ] **Task 6.2**: Write Directives: Idiomatic DSL Patterns (expanded), Version Catalog Structure, Configuration Cache Compatibility, JDK Toolchain Management, Best-Practices Consultation.
- [ ] **Task 6.3**: Write Workflows: Creating a New Module, Performance Audit, Build Logic Refactoring, Adding a Dependency, Configuring Testing, Setting Up CI/CD Builds, Enabling Dependency Locking, Publishing Artifacts.
- [ ] **Task 6.4**: Write When to Use section (7-8 scenarios).
- [ ] **Task 6.5**: Write Examples section (7 examples with JSON tool invocations and reasoning comments).
- [ ] **Task 6.6**: Write Resources section linking all reference documents (existing + new + best-practices).

## Phase 7: Sync capability purpose statements

- [ ] **Task 7.1**: Update `openspec/specs/gradle-skill/spec.md` purpose to remove "module creation" and build-authoring language; reflect trim to build execution and diagnostics only.
- [ ] **Task 7.2**: Update `openspec/specs/gradle-build-authoring/spec.md` purpose to state it's a dedicated skill (not provided via `gradle` skill).
- [ ] **Task 7.3**: Run `./gradlew :updateToolsList` if any MCP tool descriptions reference skill names.

## Phase 8: Verification

- [ ] **Task 8.1**: Verify `gradle/SKILL.md` contains zero build-authoring content (no DSL patterns, no convention plugins, no lazy API mandates). Verify it contains a cross-reference to `gradle-build-authoring`.
- [ ] **Task 8.2**: Verify `gradle-build-authoring/SKILL.md` contains zero tool-execution content (no `query_build`, no `captureTaskOutput`, no `--tests` syntax).
- [ ] **Task 8.3**: Verify all relative reference paths resolve correctly from each skill's directory.
- [ ] **Task 8.4**: Verify frontmatter descriptions follow skill-metadata spec (three-part pattern: capability + positive triggers + negative triggers).
- [ ] **Task 8.5**: Run `./gradlew zipSkills` and confirm both skills appear in `skills.zip`.
- [ ] **Task 8.6**: If any MCP tool descriptions reference skill names, run `./gradlew :updateToolsList`.
- [ ] **Task 8.7**: Run `./gradlew check` and confirm BUILD SUCCESSFUL.
- [ ] **Task 8.8**: Verify installed `gradle-build-authoring` skill can resolve `../gradle/references/common_build_patterns.md` by checking `SkillTools.installSkills` preserves relative paths in the installed layout.