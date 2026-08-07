# Capability Deltas: authoring-gradle-builds

## MODIFIED Requirements

### Requirement: Modification Index
MUST provide a workflow index for build authoring, focused on the modification lifecycle and safe application of patterns. The index SHALL include the Build Health Assessment (Doctor) workflow as `### Build Health Assessment (Doctor)`, which SHALL replace the previous `### Performance Audit` workflow in place as the sole build-health/performance assessment workflow and SHALL be bound to exactly one reference, `references/build-health-assessment.md`, which carries both the assessment procedure and the report material.

#### Scenario:
An agent needs to implement a new project module definition; it consults the `authoring-gradle-builds` body for the "Create Module" workflow, then loads the specific reference for `settings.gradle.kts` modifications.

#### Scenario: Health Assessment Routes to the Doctor
**WHEN** a request is made for a build health assessment, best-practice audit, Gradle doctor, health check, or performance audit.
**THEN** the agent SHALL route it to the `### Build Health Assessment (Doctor)` workflow, which replaces the previous `### Performance Audit` workflow in place.

## ADDED Requirements

### Requirement: Build Health Assessment Workflow
The skill SHALL provide a Build Health Assessment (Doctor) workflow that assesses a Gradle build primarily by reading its files and applying this skill's own knowledge, and reports prioritized findings as proposals. The workflow SHALL be embedded as `### Build Health Assessment (Doctor)`, replacing `### Performance Audit` in place, and SHALL be bound to exactly one reference file, `references/build-health-assessment.md`, which carries both the assessment procedure and the report material.

The workflow SHALL orient on the wrapper version and project shape, then apply the knowledge-source hierarchy defined in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)): the embedded best-practice corpus (references/best-practices/_index.md plus area pages) is the PRIMARY source to systematically read and apply per applicable area/tag; this skill (SKILL.md already read, and its references, including references/upgrading-and-release-notes.md for migration guides and release notes) is SECONDARY without re-listing their contents; gradle_docs is the SUPPLEMENT for authoritative/current version-specific detail (including wrapper-version migration guides and release notes), relying on the agent's Gradle/build knowledge to determine what applies. The workflow SHALL focus on best practices, build structure, linting, and forward-compat/deprecations, and structure hygiene such as wrapper, toolchain, and configuration hygiene (caching/parallel/configuration-cache posture) — not on verifying build task outputs or artifacts and not on hunting for logic bugs; this is not a full code review of the build logic and build-definition mistakes are incidental per the finding taxonomy — and SHALL NOT enumerate an authored check catalog and SHALL NOT define precise probe evidence contracts beyond minimal observable-signal probes and SHALL NOT add machinery to verify build task outputs or artifacts.

#### Scenario: Health Assessment Happy Path (Static-First)
**WHEN** executing a build health assessment.
**THEN** the agent SHALL:
1. **Orient**: Read `gradle/wrapper/gradle-wrapper.properties` (required) and note the wrapper major version for version-sensitive advice; read `settings.gradle(.kts)`, `gradle/libs.versions.toml` (if present), `gradle.properties`, and the module layout as optional, noting absent optional files as project shape, not findings.
2. **Assess**: Apply the hierarchy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)); record findings per the finding taxonomy and evidence tags in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)) with a doctrine pointer to the knowledge source it relies on per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).
3. **Probe (only if needed)**: Where static reading cannot produce the evidence, run the relevant cheap assessment command as an ordinary workflow step using the existing MCP tools to surface observable signals (not to verify task outputs or artifacts).
4. **Report and propose**: Finalize findings into a summary-first advisory report grouped by finding class (Build Script Errors → Forward-Compat & Risks → Recommendations → Healthy Areas) and present prioritized recommendations as proposals. This workflow is not a build-implementation audit; build-definition mistakes are incidental and not the focus.

#### Scenario: Embedded Corpus Is the Primary Source
**WHEN** assessing a build against the skill's knowledge.
**THEN** the agent SHALL apply the [Knowledge sources](references/build-health-assessment.md#knowledge-sources) hierarchy in references/build-health-assessment.md per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).

#### Scenario: Probe Used Where Static Cannot Decide
**WHEN** static reading cannot produce the evidence for a claim or finding (e.g., whether a deprecated API is actually exercised, whether configuration cache is compatible for a given task, whether version health or plugin posture matters, or what the last-known build problems were).
**THEN** the agent SHALL run the relevant cheap assessment command as an ordinary workflow step to surface observable signals — `gradle help --warning-mode all` (deprecation warnings), `gradle help --configuration-cache` (configuration-cache compatibility), `inspect_dependencies` (dependency/plugin audit), or `query_build` (last-known build problems) — using the existing MCP tools, and fold the result into the findings; probes SHALL NOT verify build task outputs or artifacts. When a probe (e.g., `gradle help --warning-mode all`) emits deprecation warnings, the agent SHALL capture each deprecation, record it as a Future Breakage finding per the finding taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)), and highlight it in the report.

#### Scenario: Probe Failed or Unavailable
**WHEN** a probe fails or is unavailable during the assessment.
**THEN** the agent SHALL continue the assessment using available static evidence, label the probe as unavailable/failed, list which tools/probes were available/ran/skipped in the report header so silent degradation stays transparent, and never guess the probe result; nothing is gated on probe execution and defect/error findings rest on direct static evidence without requiring a probe.

#### Scenario: Corpus Freshness
**WHEN** the embedded best-practices corpus does not cover the project's wrapper major version.
**THEN** the agent SHALL still read and apply the embedded corpus, note the corpus freshness in the report, and consult version-specific detail per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).

### Requirement: Doctor Findings and Report
Findings generated by the Doctor SHALL be advisory and structured for readability, and the report SHALL be summary-first and delivered in conversation, with file output only on user request. Findings SHALL carry a Type per the taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)) — `Build script errors / mistakes` (incidental) vs `Best practice / recommendation compliance` (sub-types `Future Breakage` / `Risk` / `Recommendation`) — with severity and confidence calibrated per type and evidence tagged `direct` / `observed` / `web`, and the report SHALL define its format first (Report Template) with examples illustrating the defined classes, splitting findings into classes A. Build Script Errors → B. Forward-Compat & Risks → C. Recommendations → D. Healthy Areas (scoped per class). This workflow is not a build-implementation audit; build-definition mistakes are peripheral and expected to be empty.

#### Scenario: Report Structure
**WHEN** delivering the final assessment.
**THEN** the agent SHALL use a summary-first format:
1. Title and wrapper version plus a scope note listing which tools/probes were available, ran, or were skipped and the corpus-freshness note when applicable.
2. Severity counts and prioritized recommendations (0–5, capped by the number of findings supported by evidence; zero or fewer-than-three is valid for healthy builds — do not manufacture recommendations) with per-class totals (e.g., "0 errors; 2 recommendations in Dependencies" when there are no build script errors but stale dependencies).
3. Findings grouped by class in strict order: A. Build Script Errors (with `Fix:`; incidental — expected to be empty — this workflow is not a build-implementation audit) → B. Forward-Compat & Risks (`Future Breakage` / `Risk`, each labeled with its major e.g. `Future Breakage (Gradle 10)`) → C. Recommendations → D. Healthy Areas scoped per class (e.g., "No Build Script Errors; 3 Recommendations in Dependencies — Healthy Areas: ..."); within A/B/C group by area; include Evidence tags `direct` / `observed` / `web`.
4. Optional evidence note (which probes ran, failed, or were skipped with `direct` / `observed` / `web` tags).
5. Next-step proposal with an explicit approval prompt for applying edits.

#### Scenario: Render Finding
**WHEN** reporting a build health issue.
**THEN** the agent SHALL include:
- **Type**: `Build script errors / mistakes` (incidental) or `Best practice / recommendation compliance` with sub-type `Future Breakage` / `Risk` / `Recommendation` where applicable per the taxonomy in references/build-health-assessment.md ([Knowledge sources](references/build-health-assessment.md#knowledge-sources)).
- **Area**: A free-form area heading (e.g., Structure, Build Logic, Dependencies, Performance, Reproducibility — not a closed taxonomy).
- **Severity**: One of `high`, `medium`, `low`, or `info` with a one-line rationale, calibrated per type: `Future Breakage` → `high` only if removal lands in the next Gradle major per the upgrading/release-notes references per [Knowledge sources](references/build-health-assessment.md#knowledge-sources) else `medium` (labeled `Future Breakage (Gradle 10)` or the next major with deprecation-since evidence); `Risk` → `high` if prod-facing or widening blast radius, else `medium`; `Recommendation` → `medium` / `low` / `info` by impact; `Build script errors / mistakes` (incidental) → always `high` when present.
- **Evidence**: File/line excerpt or probe snippet tagged `direct` (from the build files themselves), `observed` (output of the standard minimal probes — e.g., deprecation warnings from the help task), or `web` (version lookups); if a probe was unavailable or failed, say so here. Incidental build-definition observations, when present, rest on `direct` static evidence without requiring a probe.
- **Why**: One sentence with a doctrine pointer to the knowledge source it relies on, per [Knowledge sources](references/build-health-assessment.md#knowledge-sources).
- **Fix / Consider / Risk**: `Fix:` for incidental build-definition observations; `Consider:` / `Risk:` for compliance findings — concrete proposed edit or migration.
- **Confidence** *(optional)*: `high`/`medium`/`low` with a short reason, calibrated per type: Future Breakage `high` only if next-major removal else `medium`; Risk `medium` to `high` by exposure; Recommendation `medium`/`low`/`info`; incidental errors `high` when present.

### Requirement: Doctor Consented Remediation
The Doctor SHALL only propose fixes and SHALL never apply them silently. Remediation SHALL proceed only with explicit user approval per fix, applied through this skill's existing authoring workflows. The Doctor SHALL NOT retain a second performance workflow: `### Performance Audit` is replaced in place by `### Build Health Assessment (Doctor)`, and exactly one doctor reference file exists at `references/build-health-assessment.md`.

#### Scenario: Remediation Process
**WHEN** implementing a recommended fix from a health assessment.
**THEN** the agent SHALL:
1. Obtain explicit user approval for the specific fix.
2. Apply the fix through this skill's existing authoring workflows, routing dependency-deep work to `advanced-gradle-dependencies` via the existing handoff.
3. Re-check the fix the cheapest way (re-read the changed files or re-run the same probe) before marking it resolved.
