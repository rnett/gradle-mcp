# Capability Preservation Oracle

## Purpose
This oracle serves as a completeness gate for the Gradle skill portfolio redesign. It maps every a-priori identified unit of substantive guidance from the legacy source set to its destination in the new four-skill portfolio to ensure zero substantive content loss and no stale remnants.

## Coverage Matrix

| Source Unit / Class | Substantive Workflow / Boundary | Disposition | Destination / Split Map | Safety Constraint | Provenance | Eval ID |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `gradle/SKILL.md` | Project/Task/Prop Mapping | Split | `using-gradle/references/project-structure.md` | Read-only | authored-local | EV-USE-01 |
| `gradle/SKILL.md` | Foreground/Background Exec | Split | `using-gradle/references/running-builds.md` | Read-only | authored-local | EV-USE-02 |
| `gradle/SKILL.md` | Diagnostic tasks/monitoring | Split | `using-gradle/references/build-diagnostics.md` | Read-only | authored-local | EV-USE-03 |
| `gradle/SKILL.md` | Test execution/diagnosis | Split | `using-gradle/references/test-diagnostics.md` | Read-only | authored-local | EV-USE-04 |
| `gradle/SKILL.md` | Gradle docs/internals | Split | `using-gradle/references/gradle-internals.md` | Read-only | authored-local | EV-USE-05 |
| `gradle/SKILL.md` | Build definition mutation | Split | `authoring-gradle-builds/SKILL.md` & refs | Sole mutator | authored-local | EV-AUT-01 |
| `gradle-build-authoring/SKILL.md` | Build authoring core | Migrate | `authoring-gradle-builds/SKILL.md` | No `afterEvaluate` | authored-local | EV-AUT-02 |
| `gradle-build-authoring/refs/...` | All 10 gap-filling refs | Migrate | `authoring-gradle-builds/references/*.md` | Root-local | authored-local | EV-AUT-03 |
| `gradle-build-authoring/refs/best-practices/`| Generated Best Practices | Migrate | `authoring-gradle-builds/references/best-practices/` | Set-based | generated | EV-AUT-04 |
| `exploring_dependency_sources/SKILL.md`| Source search/reading | Merge | `using-gradle/references/dependency-sources.md` | Read-only | authored-local | EV-USE-06 |
| `exploring_dependency_sources/refs/...`| Internal source research | Merge | `using-gradle/references/gradle-internals.md` | Read-only | authored-local | EV-USE-07 |
| `managing_gradle_dependencies/SKILL.md`| Auditing / Conflict Inspect | Merge | `using-gradle/references/dependency-inspection.md` | Read-only | authored-local | EV-USE-08 |
| `managing_gradle_dependencies/SKILL.md`| Versions / Updates | Merge | `using-gradle/references/dependency-updates.md` | Read-only | authored-local | EV-USE-09 |
| `managing_gradle_dependencies/SKILL.md`| Dependency additions | Merge | `authoring-gradle-builds/references/dependency-declaration.md` | Sole mutator | authored-local | EV-AUT-05 |
| `interacting_with_project_runtime/SKILL.md`| REPL Lifecycle / Probing | Compact | `interacting-with-project-runtime/SKILL.md` | Stop -> Start | authored-local | EV-RUN-01 |
| `verifying_compose_ui/SKILL.md` | Rendering / Transitions | Compact | `verifying-compose-ui/SKILL.md` | JVM/Desktop only | authored-local | EV-UI-01 |
| `shared/runtime-compose-setup.md` | Common JVM Plumbing | Extract | `authored-shared` $\rightarrow$ both runtime/ui | Idempotent | authored-shared | EV-INF-01 |

## Completeness Gate
The redesign is considered complete only when:
1. Every entry in the "Source Unit" column has been verified as present in the "Destination" location.
2. A residue search for all retired names (`gradle-skill`, `gradle-build-authoring`, `managing-gradle-dependencies`, `exploring-dependency-sources`) returns zero matches in the final `skills.zip` content.
3. Each item in the "Eval ID" column has a corresponding qualitative test case in the behavioral corpus.
