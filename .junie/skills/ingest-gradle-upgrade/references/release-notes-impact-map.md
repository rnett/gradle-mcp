# Release Notes Impact Map

Triage aid for step 4 of the ingest-gradle-upgrade workflow. Tag each material ledger item with exactly one domain from Table A, then open the mapped surfaces. Tables B and C are the grep-verified hotspot inventories (seed, not exhaustive — rerun the discovery patterns every ingestion). All paths are relative to the repo root; line numbers were verified at wrapper 9.6.1 and will drift.

## Table A — Release-note domain → surfaces

| Release-note domain | Priority | Project-code surfaces | Shipped-skill surfaces |
|---|---|---|---|
| Configuration Cache improvements | High | `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/BuildExecutionService.kt`, `RunningBuild.kt` (event/progress capture under config cache); config-cache-sensitive tests | `using-gradle/SKILL.md` footguns; `using-gradle/references/troubleshooting.md`; `authoring-gradle-builds/references/advanced-configuration.md` |
| Isolated Projects | High | Tooling-layer decoupling: `src/main/kotlin/dev/rnett/gradle/mcp/gradle/GradleProvider.kt`, `gradle/GradleConnectionService.kt` | `authoring-gradle-builds/SKILL.md` Compatibility Quick-Reference (heading at line 41, "Project isolation" row) |
| CLI, logging, and problem reporting | High | Console/problem parsing: `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/ProblemsAccumulator.kt`, `FailureIndexer.kt`; `--warning-mode` usage in `gradle/GradleArgs.kt` | `using-gradle/references/running-builds.md`, `troubleshooting.md`, `diagnostic-tasks.md` |
| Test reporting and execution | High | `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/TestCollector.kt`, `BuildExecutionService.kt`; `jvm-test-suite` usage in `build.gradle.kts` | `using-gradle/references/testing.md`; `authoring-gradle-builds/references/testing-configuration.md` |
| Core plugin and plugin authoring enhancements | High | Init scripts `src/main/resources/init-scripts/dependencies-report.init.gradle.kts`, `task-out.init.gradle.kts`, `repl-env.init.gradle.kts`, `scans.init.gradle`; model queries in `gradle/GradleProvider.kt` | `authoring-gradle-builds/references/plugin-development.md`, `custom-tasks.md`, `worker-api.md`, `convention-plugins.md` |
| Tooling and IDE integration | High | `org.gradle.tooling` usage: `src/main/kotlin/dev/rnett/gradle/mcp/gradle/GradleConnectionService.kt`, `GradleProvider.kt`, `GradleArgs.kt` | `using-gradle/references/research.md`; `interacting-with-project-runtime/SKILL.md` + `references/repl-session-setup.md` |
| Dependency management enhancements | Medium | `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt`; `src/main/resources/init-scripts/dependencies-report.init.gradle.kts` | `advanced-gradle-dependencies/references/resolution-mechanics.md`; `using-gradle/references/dependencies.md`; `authoring-gradle-builds/references/dependencies-and-catalogs.md` |
| Build authoring improvements | Medium | `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml` | `authoring-gradle-builds/SKILL.md` + `references/kotlin-dsl.md`, `build-lifecycle.md`, `modules-and-settings.md` |
| Performance improvements | Medium | Invocation defaults (parallelism, cache flags) in `src/main/kotlin/dev/rnett/gradle/mcp/gradle/GradleArgs.kt` | `using-gradle/references/build-environment.md` |
| Security and infrastructure | Medium | Wrapper checksum handling (`validateDistributionUrl` in `gradle/wrapper/gradle-wrapper.properties`); JVM requirements vs `jvmToolchain(21)` | `using-gradle/SKILL.md` wrapper-pinning footgun; `using-gradle/references/troubleshooting.md` |
| Promoted features | Always | — | All incubating/stable language across skills — see Table B lifecycle hotspots |
| Fixed issues / Known issues | Always | Repo workarounds — grep issue numbers and `workaround` (Table C) | Troubleshooting references: `using-gradle/references/troubleshooting.md`, `authoring-gradle-builds` and `advanced-gradle-dependencies` references with `workaround` hunks (Table B) |
| Documentation and training | Always | Docs services: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/gradle/docs/{ContentExtractorService,GradleDocsIndexService,GradleDocsService,HtmlConverter,MarkdownService}.kt` (page-structure changes can break extraction/indexing) | Every `gradle_docs(path=...)` pointer in shipped skills — see Table B pointer hotspots |

Any other subsection present in the notes: map it with the discovery patterns below; if no surface matches, it is non-material and gets covered by the sentinel row of its containing section.

## Table B — Shipped-skill version-sensitive hotspots (grep-verified at wrapper 9.6.1)

Lifecycle language (`incubating`):
- `advanced-gradle-dependencies/references/resolution-mechanics.md:86`
- `authoring-gradle-builds/SKILL.md:46-47`
- `authoring-gradle-builds/references/advanced-configuration.md:326`
- `authoring-gradle-builds/references/build-lifecycle.md:95,98,108`
- `authoring-gradle-builds/references/managed-types-and-providers.md:290,292,300`
- `using-gradle/SKILL.md:86`

Lifecycle language (`experimental`, 18 hunks):
- `authoring-gradle-builds/SKILL.md:46-47`
- `authoring-gradle-builds/references/build-lifecycle.md:110`
- `authoring-gradle-builds/references/ci-cd-builds.md:83`
- `authoring-gradle-builds/references/custom-tasks.md:194`
- `authoring-gradle-builds/references/dependencies-and-catalogs.md:72,101,304,334`
- `authoring-gradle-builds/references/kotlin-compiler-options.md:113,120`
- `authoring-gradle-builds/references/modules-and-settings.md:103,201`
- `using-gradle/SKILL.md:86`
- `using-gradle/references/research.md:79`
- `using-gradle/references/troubleshooting.md:92`
- `interacting-with-project-runtime/SKILL.md:8`
- `interacting-with-project-runtime/references/repl-session-setup.md:12`
- `verifying-compose-ui/references/repl-session-setup.md:12`

Compatibility quick-reference tables (update rows to NEW):
- `authoring-gradle-builds/SKILL.md:41` (heading `Compatibility Quick-Reference`)
- `using-gradle/SKILL.md:80` (heading `Compatibility Quick-Reference`)

Upgrade-guide / release-notes pointers (validate page names and links per delta):
- `authoring-gradle-builds/SKILL.md:36`
- `authoring-gradle-builds/references/upgrading-and-release-notes.md:18-21,25-26,31-33,48-50` — this reference is itself an upgrade artifact: its page pointers and migration checklists move with every delta
- `using-gradle/references/research.md:16`

`afterEvaluate` hunks (safety rule applies to any edit near them — see the shipped-skills checklist):
- `authoring-gradle-builds/SKILL.md:63,77`
- `authoring-gradle-builds/references/build-lifecycle.md:77,81,85,110`
- `authoring-gradle-builds/references/convention-plugins.md:105`
- `authoring-gradle-builds/references/custom-tasks.md:31,110`
- `authoring-gradle-builds/references/testing-configuration.md:14`

`workaround` wording (check against `Fixed issues` each delta):
- `advanced-gradle-dependencies/references/component-metadata-rules.md:91-93`
- `authoring-gradle-builds/references/advanced-configuration.md:272`
- `authoring-gradle-builds/references/artifact-publishing.md:30`
- `authoring-gradle-builds/references/build-scans.md:53`
- `authoring-gradle-builds/references/plugin-development.md:160`
- `using-gradle/references/diagnostic-tasks.md:35`
- `verifying-compose-ui/references/troubleshooting.md:74-75`

Per-version notes blocks: `authoring-gradle-builds/references/*` carry `Version notes` / `Version-sensitive field-guide rule` blocks across `advanced-configuration.md` (including per-version notes like "Gradle 9.0: …"), `build-lifecycle.md`, `testing-configuration.md`, `kotlin-dsl.md`, `managed-types-and-providers.md`, `modules-and-settings.md`, `plugin-development.md`, `worker-api.md`, `java-builds.md`, `jdk-toolchains.md`, `artifact-publishing.md`, `ci-cd-builds.md`, `composite-builds.md`, `configurations-and-variants.md`, `continuous-builds.md`, `convention-plugins.md`, `custom-tasks.md`, `dependencies-and-catalogs.md`, `dependency-locking.md`, `build-scans.md`.

Low-churn skills: `interacting-with-project-runtime` and `verifying-compose-ui` are largely version-agnostic — check only JVM/toolchain requirements and the lifecycle hunks listed above.

## Table C — Project-code hotspots (confirmed surfaces)

- Tooling API layer: `src/main/kotlin/dev/rnett/gradle/mcp/gradle/{GradleConnectionService,GradleProvider,GradleArgs,InitScriptProvider}.kt`
- Execution/events: `src/main/kotlin/dev/rnett/gradle/mcp/gradle/build/{BuildExecutionService,TestCollector,ProblemsAccumulator,FailureIndexer,RunningBuild}.kt`
- Init scripts: `src/main/resources/init-scripts/{dependencies-report.init.gradle.kts,repl-env.init.gradle.kts,scans.init.gradle,task-out.init.gradle.kts}`
- Dependency reporting: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/GradleDependencyService.kt`
- Docs ingestion: `src/main/kotlin/dev/rnett/gradle/mcp/dependencies/gradle/docs/{ContentExtractorService,GradleDocsIndexService,GradleDocsService,HtmlConverter,MarkdownService}.kt`
- REPL stack (embedded Kotlin/Groovy version changes): `src/main/kotlin/dev/rnett/gradle/mcp/repl/{ReplManager,ReplEnvironmentService}.kt`; `repl-worker/` (`GlobalResponder`, `KotlinScriptEvaluator`, `Logger`, `ReplOutputStream`, `ReplWorker`, `Responder`, `ResultRenderer` + tests); `repl-shared/` (`ReplProtocol.kt`)
- Build scripts: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`
- Best-practices generator: `best-practices-generator/` (`build.gradle.kts`, `src/main/kotlin/dev/rnett/gradle/mcp/bestpractices/GenerateBestPracticesDoc.kt`, test + fixtures)
- Version-pinned tests (review, don't blindly bump — some pin OLD versions deliberately):
  - `src/test/kotlin/dev/rnett/gradle/mcp/GradleVersionServiceTest.kt` (8.7/8.6)
  - `src/test/kotlin/dev/rnett/gradle/mcp/tools/GradleDocsVersionDetectionTest.kt` (8.5/7.6.3)
  - `src/test/kotlin/dev/rnett/gradle/mcp/dependencies/gradle/docs/GradleDocsServiceTest.kt` (9.4.0 fixtures)
  - `src/integrationTest/kotlin/dev/rnett/gradle/mcp/e2e/GradleVersionResolutionIntegrationTest.kt:76` (9.9.9)
- Records: `.junie/playbook.md` (wrapper version line, Build Verification section)

## Discovery patterns (rerun every ingestion — inventories drift)

Shipped skills — search `src/main/skills`, `*.md` files:
1. `incubating|experimental` (case-insensitive) — lifecycle language vs `Promoted features`
2. `Gradle \d` — explicit version mentions
3. `Version notes|version-sensitive|Compatibility Quick-Reference` — version-scoped blocks and tables
4. `upgrading_version|upgrading_major_version|release-notes` — upgrade-guide/release-notes pointers
5. `gradle_docs\(` — every tool pointer; validate each `path=` against `gradle_docs(path=".", version="NEW")`
6. `afterEvaluate` — safety-rule surface
7. `workaround|Workaround` — fix-obsoolution candidates

Repo code — search `src/main`, `src/test`, `src/integrationTest`, `*.kt`/`*.kts`/init scripts:
8. `<OLD version literal>` — the OLD version escaped as a regex, written `9\.4\.1` when OLD is 9.4.1 — and `<NEW major>\.`: hardcoded versions
9. `workaround|Workaround` — code-level workarounds vs `Fixed issues`
10. `org\.gradle\.tooling` — Tooling API usage vs Tooling/IDE-integration notes
11. `gradleVersion|GradleVersion` in test sources — version-pinned assertions

## Lifecycle-language rules

- **Promoted incubating → stable**: remove "(incubating)"/experimental hedges at every Table B location for that feature; update stable-since versions where the skill tracks them; update the matching Compatibility Quick-Reference row.
- **Removed features**: delete or rewrite guidance immediately; never leave a pointer at a removed API or page.
- **Newly adopted experimental features**: label lifecycle status and version range exactly as the existing compatibility tables do.
- **Deprecations from in-range blocks**: fix the repo usage; in skills, mark the deprecated pattern and name its replacement and removal version when the upgrade guide states one.
