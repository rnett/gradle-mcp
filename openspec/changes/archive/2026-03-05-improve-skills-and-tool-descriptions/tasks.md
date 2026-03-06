## 1. Audit and Preparation

- [x] 1.1 Perform a complete audit of all current skill and tool descriptions.
- [x] 1.2 Identify specific gaps in "When to Use" scenarios, authoritative terminology, gerund-first descriptions, and skill naming.

## 2. Skill Description and Naming Refinement

- [x] 2.1 Audit and update `managing_gradle_builds/SKILL.md` for authoritative descriptions and gerund compliance.
- [x] 2.2 Audit and update `gradle-test/SKILL.md` to highlight surgical failure isolation and gerund compliance.
- [x] 2.3 Audit and update `gradle-dependencies/SKILL.md` for authoritative graph auditing and gerund compliance.
- [x] 2.4 Audit and update `gradle-introspection/SKILL.md` for project mapping and gerund compliance.
- [x] 2.5 Audit and update `gradle-library-sources/SKILL.md` for surgical search and gerund compliance.
- [x] 2.6 Audit and update `gradle-repl/SKILL.md` for interactive prototyping and gerund compliance.
- [x] 2.7 Audit and update `gradle-docs/SKILL.md` for documentation retrieval and gerund compliance.
- [x] 2.8 Audit and update `compose-view/SKILL.md` for visual verification and gerund compliance.
- [x] 2.9 (If needed) Rename skills to use the action-oriented gerund form.

## 3. Tool Description Refinement (Kotlin)

- [x] 3.1 Refine `gradle` and `inspect_build` tool descriptions in `GradleExecutionTools.kt` for gerund compliance.
- [x] 3.2 Update `inspect_dependencies` tool description in `GradleDependencyTools.kt` for gerund compliance.
- [x] 3.3 Update `read_dependency_sources` and `search_dependency_sources` in `DependencySourceTools.kt` for gerund compliance.
- [x] 3.4 Polish `search_maven_central` description in `DependencySearchTools.kt` for gerund compliance.
- [x] 3.5 Refine `gradle_docs` description in `GradleDocsTools.kt` for gerund compliance.
- [x] 3.6 Improve `install_gradle_skills` description in `SkillTools.kt` for gerund compliance.

## 4. Parameter Polish

- [x] 4.1 Audit all `@Description` annotations for tool parameters.
- [x] 4.2 Update parameter descriptions to be more descriptive and provide clearer usage hints.

## 5. Synchronization and Verification

- [x] 5.1 Perform a final cross-cutting synchronization audit to ensure terminology consistency.
- [x] 5.2 Run `./gradlew :updateToolsList` to update the markdown tool references in `docs/tools/`.
- [x] 5.3 Verify all tests pass and ensure no regressions in tool definitions.
