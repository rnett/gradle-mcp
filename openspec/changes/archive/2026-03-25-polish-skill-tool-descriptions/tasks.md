## 1. Spec Updates

- [x] 1.1 Archive delta specs into `openspec/specs/skill-and-tool-descriptions/spec.md` and `openspec/specs/skill-metadata/spec.md`

## 2. Skill Frontmatter Audit and Rewrite

- [x] 2.1 Activate `skill_authoring` and audit `skills/gradle_expert/SKILL.md` frontmatter description; rewrite to 1-2 sentences (three-part structure: capability+anchors, positive trigger, negative trigger)
- [x] 2.2 Audit and rewrite `skills/running_gradle_builds/SKILL.md` frontmatter description
- [x] 2.3 Audit and rewrite `skills/running_gradle_tests/SKILL.md` frontmatter description
- [x] 2.4 Audit and rewrite `skills/searching_dependency_sources/SKILL.md` frontmatter description
- [x] 2.5 Audit and rewrite `skills/introspecting_gradle_projects/SKILL.md` frontmatter description
- [x] 2.6 Audit and rewrite `skills/managing_gradle_dependencies/SKILL.md` frontmatter description
- [x] 2.7 Audit and rewrite `skills/researching_gradle_internals/SKILL.md` frontmatter description
- [x] 2.8 Audit and rewrite `skills/interacting_with_project_runtime/SKILL.md` frontmatter description
- [x] 2.9 Audit and rewrite `skills/verifying_compose_ui/SKILL.md` frontmatter description

## 3. Tool Description Audit and Rewrite

- [x] 3.1 Activate `mcp_authoring` and audit `GradleExecutionTools.kt` — rewrite tool description to 1-2 sentence opening + essential "how to" only; cap `@Description` annotations to under 100 characters
- [x] 3.2 Audit and tighten `GradleBuildLookupTools.kt` (`inspect_build`) tool descriptions and annotations
- [x] 3.3 Audit and tighten `DependencySourceTools.kt` (`search_dependency_sources`, `read_dependency_sources`) descriptions and annotations
- [x] 3.4 Audit and tighten `DependencySearchTools.kt` descriptions and annotations
- [x] 3.5 Audit and tighten `GradleDependencyTools.kt` descriptions and annotations
- [x] 3.6 Audit and tighten `GradleDocsTools.kt` descriptions and annotations
- [x] 3.7 Audit and tighten remaining tool files (`GradleInputs.kt`, REPL tools, skill tools)

## 4. Reference Docs and Validation

- [x] 4.1 Run `./gradlew :updateToolsList` to regenerate `docs/tools/*.md` from updated source
- [x] 4.2 Validate all skill descriptions still route correctly (review with `skill_authoring`)
- [x] 4.3 Validate all tool descriptions still meet `mcp_authoring` quality criteria
- [x] 4.4 Restore collapsed single-line tag list in `GradleDocsTools.kt` to proper multiline bullets; ensure all tool descriptions use multiline lists throughout
