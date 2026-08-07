# Build Health Assessment (Doctor)

A knowledge-driven health assessment focused on best practices, build structure, linting, and forward-compat — not the implementation of the build itself. **Focus on** best practices, build structure, linting, forward-compat/deprecations, and structure hygiene such as wrapper, toolchain, and configuration hygiene (caching/parallel/configuration-cache posture). **This is not a full code review of the build logic** — the agent should not go looking for bugs or errors in the build scripts themselves. This workflow is not a build-implementation audit; it orients on the wrapper version and project shape and reports prioritized best-practice findings as proposals. Build-definition mistakes are incidental and flagged only when obvious from static reading (e.g., a referenced path that does not exist), not by hunting for logic bugs; for knowledge-source precedence see [Knowledge sources](#knowledge-sources) below.

Minimal probes surface observable signals only where static reading is insufficient (e.g., `gradle help --warning-mode all` for deprecations). Findings are advisory and reported summary-first; fixes are proposed and applied only with explicit user approval. The assessment does not verify build task outputs or artifacts.

## When to Use
- Build health checks or best-practice audit requests.
- Pre-migration audits before a wrapper upgrade or structural change.
- CI hygiene passes to catch deprecation, laziness, or reproducibility drift.
- Performance audits (covering eager APIs, provider realization, configuration-phase resolution, cross-project coupling, configuration-cache violations, and build-cache/parallel posture).

### Knowledge sources

- **PRIMARY**: [Embedded best-practices corpus](best-practices/_index.md) — the PRIMARY source the doctor systematically reads and applies (`references/best-practices/_index.md` area summaries and Browse-by-Tag → area pages), reviewing the build against each applicable area or tag.
- **SECONDARY**: This skill — `SKILL.md` (already read when this workflow loads) and its references, consulted per area without re-listing their contents; migration guides and release notes for wrapper upgrades, deprecations, and breaking changes are in `references/upgrading-and-release-notes.md`.
- **SUPPLEMENT**: `gradle_docs` — authoritative/current version-specific detail, including the migration guides and release notes for the wrapper's version, e.g. `gradle_docs(path="userguide/upgrading_version_<N>.md")` and `gradle_docs(query="tag:release-notes")`.

The embedded corpus is read-only and must not be edited.

## Orientation: Project Shape
Read the following files in order to note the project shape. Orientation notes describe the build's state without emitting findings.
1. `gradle/wrapper/gradle-wrapper.properties` (**required**): Note the wrapper major version for version-sensitive advice.
2. `settings.gradle(.kts)`: Locate the DSL file; read `pluginManagement`, `dependencyResolutionManagement`, and the `include(...)` module list.
3. `gradle/libs.versions.toml`: If present, note the catalog entries.
4. `gradle.properties`: Note caching, parallel, and configuration-cache flags.
5. Module layout: Enumerate directories containing `build.gradle(.kts)`; note `buildSrc/`, `build-logic/`, or `gradle/plugins/` if present.
6. Module analysis: For each module, note applied plugins and publishing/testing wiring to determine relevance.

Absent optional files and single-module layouts are noted as project shape, not as findings, unless context makes them relevant.

## Knowledge-Driven Assessment
### Primary Workflow
1. Orient on the wrapper version and project shape per [Orientation: Project Shape](#orientation-project-shape).
2. Survey area summaries and the Browse-by-Tag cross-reference per [Knowledge sources](#knowledge-sources).
3. Read relevant area pages per [Knowledge sources](#knowledge-sources).
4. Systematically review the build's files against each applicable area or tag per [Knowledge sources](#knowledge-sources).
5. Record each finding with its type and evidence tag, severity/confidence per [Findings Recording](#findings-recording), and a rationale pointer to the knowledge source it relies on per [Knowledge sources](#knowledge-sources) (formatting per [Execution Guidance](#execution-guidance)).
6. Where static reading is insufficient, run a minimal probe per [Minimal Probes](#minimal-probes) and fold the evidence into the findings; when a probe emits deprecation warnings, capture each deprecation, record it as a finding, and highlight it in the report — deprecations are Future Breakage findings per [Finding Taxonomy](#finding-taxonomy).

### Execution Guidance
Perform a systematic sweep of the build's files against applicable areas/tags per [Knowledge sources](#knowledge-sources) (e.g., structure/settings, build logic, performance/caching, reproducibility/upgrading).

- **Freshness Note**: If the embedded corpus does not cover the project's newer Gradle wrapper version, add a freshness note to the report that the assessment may miss recent behavior; for version-specific detail see [Knowledge sources](#knowledge-sources).
- **Doctrine Pointers**: Every finding must include a rationale pointer to the knowledge source it relies on, per [Knowledge sources](#knowledge-sources).
- **Application**: Read build files, then check relevance against applicable areas/tags per [Knowledge sources](#knowledge-sources). If context is ambiguous, use a minimal probe or consult [Knowledge sources](#knowledge-sources) to confirm.
- **Scope**: The doctor focuses on best practices, build structure, linting, and forward-compat/deprecations, and structure hygiene such as wrapper, toolchain, and configuration hygiene — not on verifying task outputs or artifacts and not on hunting for logic bugs. **This is not a full code review of the build logic**; build-definition mistakes are incidental and not the focus per [Finding Taxonomy](#finding-taxonomy).

## Minimal Probes
Probes are optional but recommended. Run them as ordinary workflow steps using existing MCP tools. They surface observable signals — they do not verify build task outputs or artifacts.

- **Deprecation warnings**: `gradle help --warning-mode all` (or `tasks --warning-mode all`). Use to confirm that deprecated APIs are being exercised. Findings from this probe are tagged `observed`.
- **Wrapper version**: `gradle --version` or `gradle/wrapper/gradle-wrapper.properties` (already read during orientation) — static signal for version-sensitive advice.
- **Configuration-cache compatibility**: `gradle help --configuration-cache`. Use when static reading suggests non-compatible patterns or when specifically requested for non-trivial builds.
- **Dependency / plugin audit**: `inspect_dependencies`. Use to support recommendations regarding version health or plugin posture when relevant; when unavailable, continue with static evidence.
- **Build problems**: `query_build` / Problems API. Use as passive evidence from recent builds; do not trigger new builds solely for diagnosis.

If a probe fails or is unavailable, continue with available static evidence and label the finding as "probe unavailable/failed — assessed from static files". The report header lists which tools/probes were available, ran, or were skipped so silent degradation (e.g., absent `inspect_dependencies` or `gradle_docs`) stays transparent; nothing is gated on probe execution.

When a probe (e.g., `gradle help --warning-mode all`) emits deprecation warnings, capture each deprecation, record it as a finding (or an equivalent flag), and highlight it in the report so deprecated usage is explicit.

## Findings, Reporting, and Remediation
### Report Template
Deliver a summary-first report in conversation:

1. **Header**: Title, wrapper version, and a scope note listing which tools/probes were available, ran, or were skipped and the corpus-freshness note when applicable.
2. **Summary**: Severity counts and prioritized recommendations (0–5, supported by evidence) with per-class totals, e.g., "0 errors; 2 recommendations in Dependencies" for a repo with zero incidental errors but stale deps. Do not manufacture recommendations for healthy builds.
3. **Details — Findings grouped by class in strict order**:
   - **A. Build Script Errors** — incidental defects/mistakes with `Fix:` directives; expected to be empty — this workflow is not a build-implementation audit and errors are incidental.
   - **B. Forward-Compat & Risks** — `Future Breakage` (deprecations) and `Risk/Hazard` findings, each labeled with its major (e.g., `Future Breakage (Gradle 10)`).
   - **C. Recommendations** — remaining best-practice improvements.
   - **D. Healthy Areas** — scoped per class (e.g., "No Build Script Errors; 3 Recommendations in Dependencies — Healthy Areas: Structure, Build Logic, Reproducibility have no findings").
   Within A/B/C, group by area.
4. **Evidence Note** *(optional)*: Details on probe results with `direct` / `observed` / `web` tags.
5. **Next Steps**: Proposal with an explicit approval prompt (e.g., "Shall I apply `<fix>` via `<workflow>`?").

File output is provided only on explicit user request.

### Finding Taxonomy
Every finding carries a **Type** that determines its framing and severity calibration. This workflow is a best-practice/structure/linting/forward-compat assessment — not a build-implementation audit and **not a full code review of the build logic**. The report splits into two top-level finding classes, with build-definition mistakes as the peripheral, incidental class:

- **Best practice / recommendation compliance** — the primary focus: best practices, build structure, linting, forward-compat/deprecations, wrapper/toolchain/configuration hygiene, and risks. Reported with `Consider:` / `Risk:` framing tied to the knowledge sources per [Knowledge sources](#knowledge-sources).
- **Build script errors / mistakes** — incidental observations only, flagged when obvious from static reading without running tasks (e.g., a referenced path that does not exist), not by hunting for logic bugs. This workflow is not a build-implementation audit and **this is not a full code review of the build logic**; this class is peripheral, expected to be empty in most assessments, and must not dominate the report. When present, reported with a `Fix:` directive and `Evidence: direct`.

Compliance findings use sub-types for severity calibration:
- **Future Breakage** — deprecations or forward-compat issues that will break on a future major (e.g., deprecation warnings surfaced by the help probe).
- **Risk/Hazard** — correct now but unsafe posture (e.g., pre-release dependency in a production-facing module, unpinned catalog entry, loose repository posture).
- **Recommendation** — best-practice improvement with no immediate breakage risk (e.g., missing Java toolchain declaration, wrapper/configuration hygiene, caching/parallel posture).

### Findings Recording
Record each finding as a short structured block:

- **Type**: One of `Build script errors / mistakes` (incidental) or `Best practice / recommendation compliance` (`Future Breakage` / `Risk` / `Recommendation` sub-type where applicable).
- **Area**: Free-form heading (e.g., Structure, Build Logic, Dependencies, Performance, Reproducibility).
- **Severity**: `high` / `medium` / `low` / `info` with a one-line rationale, calibrated per type:
  - `Build script errors / mistakes` (incidental) → always `high` when present.
  - `Future Breakage` → `high` only if removal lands in the next Gradle major per the upgrading/release-notes references per [Knowledge sources](#knowledge-sources), else `medium`; label as `Future Breakage (Gradle 10)` (or the next major) with deprecation-since evidence.
  - `Risk` → `high` if prod-facing or widening blast radius, else `medium`.
  - `Recommendation` → `medium` / `low` / `info` by impact.
- **Evidence**: File/line excerpt or probe snippet tagged with one of `direct` (from the build files themselves), `observed` (output of the standard minimal probes — e.g., deprecation warnings from the help task), or `web` (version lookups — e.g., Maven Central). Incidental build-definition observations, when present, rest on `direct` static evidence — no probe required and no "hypothesized defect; run a probe" step.
- **Why**: One sentence with a doctrine pointer to the knowledge source it relies on, per [Knowledge sources](#knowledge-sources).
- **Fix / Consider / Risk**: `Fix:` for incidental build-definition observations; `Consider:` / `Risk:` for compliance findings — concrete proposed edit or migration.
- **Confidence** *(optional)*: `high` / `medium` / `low` with a short reason, calibrated per type: errors `high` when present; Future Breakage `high` only if removal lands in the next Gradle major per the upgrading/release-notes references per [Knowledge sources](#knowledge-sources) else `medium`; Risk `medium` to `high` by exposure; Recommendation `medium`/`low`/`info`; web-fallback findings `medium`.

High only if removal lands in the next Gradle major — forward-compat calibration per [Knowledge sources](#knowledge-sources): deprecations surfaced by the help probe are Future Breakage findings with severity `high` only if removal lands in the next Gradle major (per the upgrading/release-notes references), else `medium`.


The following examples illustrate the report classes and evidence tags defined in [Report Template](#report-template) and [Finding Taxonomy](#finding-taxonomy):

**Example (forward-compat — observed):**
```markdown
**Area**: Build Logic
**Type**: Best practice / recommendation compliance — Future Breakage (Gradle 10)
**Severity**: `high` — deprecated API removal lands in the next Gradle major per the upgrading/release-notes references per [Knowledge sources](#knowledge-sources).
**Evidence**: `observed` — `gradle help --warning-mode all` — `PropertyConvention has been deprecated since 8.5 and will be removed in Gradle 10.0`
**Why**: Deprecations slated for next-major removal are forward-compat breakage — per [Knowledge sources](#knowledge-sources).
**Consider**: Replace with `convention(...)` provider wiring before upgrading to the next major.
**Confidence**: `high` — observed probe output + version-specific detail per [Knowledge sources](#knowledge-sources)
```

**Example (recommendation — direct):**
```markdown
**Area**: Toolchains
**Type**: Best practice / recommendation compliance — Recommendation
**Severity**: `low` — missing Java toolchain declaration reduces reproducibility.
**Evidence**: `direct` — `build.gradle.kts:12` — no `java { toolchain { ... } }` / `jvmToolchain(...)` declaration
**Why**: Explicit toolchain wiring improves reproducibility and cacheability — per [Knowledge sources](#knowledge-sources).
**Consider**: Add `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }` via the toolchain workflow.
**Confidence**: `medium` — statically verified from the build files themselves
```

**Example (risk — direct):**
```markdown
**Area**: Dependencies
**Type**: Best practice / recommendation compliance — Risk
**Severity**: `high` — pre-release dependency in a production-facing module widens blast radius.
**Evidence**: `direct` — `gradle/libs.versions.toml:8` — `kotlin = "2.2.0-RC"` referenced by `:app` (prod-facing) — `build.gradle.kts:18` — `alias(libs.plugins.kotlin.jvm)`
**Why**: Pre-release dependencies in prod-facing modules risk forward-compat and reproducibility — per [Knowledge sources](#knowledge-sources).
**Risk**: Pin to a stable release or isolate the pre-release to a non-prod module.
**Confidence**: `medium` — statically verified from the build files themselves
```

### Remediation
- **Proposals Only**: Findings are proposals; never edit files without explicit user approval.
- **Workflow Application**: Apply approved fixes via this skill's authoring workflows. Route dependency-deep work to `advanced-gradle-dependencies`.
- **Verification**: After applying a fix, re-verify using the cheapest method (re-read files or re-run the probe) before marking it resolved.

### Scope Boundaries
- **Single Reference**: Exactly one doctor reference file (`references/build-health-assessment.md`) contains both procedure and report material.
- **No Tooling Changes**: No new MCP tools.
- **No Heavy Execution**: No full build/test execution for diagnosis; no `--scan` by default. Probes are limited to observable signals (e.g., `gradle help --warning-mode all`, `--version`); do not add machinery to verify build task outputs or artifacts.
- **Project Shape**: Absent optional files are project shape, not findings, unless context makes them relevant.
- **Focus**: The doctor focuses on best practices, build structure, linting, and forward-compat — not the implementation of the build itself. This workflow is not a build-implementation audit; build-definition mistakes are incidental and not the workflow's focus.
