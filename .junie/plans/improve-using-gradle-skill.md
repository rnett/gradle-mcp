---
sessionId: session-260731-using-gradle-cli-daemon-env
---

# Requirements

### Overview & Goals

Extend the already-rewritten `using-gradle` skill so it authoritatively covers four more Gradle user-guide pages, always from the angle of **a feature-oriented software engineer *operating* an existing build** (not authoring build logic — that is `authoring-gradle-builds`). The audience is an **AI agent**, so prose stays dense, directive, opinionated, version-aware (Gradle 7/8/9, biased to latest), and machine-consumable. This continues the same coverage program that added the "Learning Gradle Basics" material.

Target pages (read the live pages, do not work from memory):
- `https://docs.gradle.org/current/userguide/command_line_interface.html` — **not necessarily every single option**; the options a feature engineer actually reaches for.
- `https://docs.gradle.org/current/userguide/gradle_daemon.html` — the operative opinion is **always use the daemon**.
- `https://docs.gradle.org/current/userguide/directory_layout.html`.
- `https://docs.gradle.org/current/userguide/build_environment.html` — flagged by the user as **probably the most important**; give it depth.

Goals:
- Guarantee every high-value topic from those four pages is represented somewhere in `using-gradle`, each in a single home, with the operating-angle framing.
- Be opinionated (state the default and the anti-pattern), version-aware (inline `Version notes` + 7.x fallbacks where behavior differs), and route to authoritative docs (verified version-scoped `gradle_docs` hints + published `docs.gradle.org` URLs) and to relevant published MCP-tool pages.
- Preserve the skill's house style and portfolio contract (operation-vs-modification boundary, progressive disclosure, provenance headers, lean always-loaded `SKILL.md`).

### Scope

#### In Scope
- `src/main/skills/using-gradle/SKILL.md` and its six references (`build-orientation.md`, `running-builds.md`, `testing.md`, `troubleshooting.md`, `dependencies.md`, `research.md`) — enrich in place and/or add a new reference if the Stage 1 brief justifies a new single home (e.g. a build-environment reference). The brief fixes exact homes.
- `SKILL_INDEXES["using-gradle"]` manifest in `src/main/kotlin/dev/rnett/gradle/mcp/skills/SkillMaterialization.kt` — only if a new reference is added or routing changes (drives generated `references/_index.md`).
- OpenSpec delta `openspec/changes/redesign-gradle-skills-portfolio/specs/using-gradle/spec.md` — reconcile if new requirements/scenarios are warranted by the added coverage.
- A Stage 1 coverage brief under `reports/`.

#### Out of Scope
- The other three skills' content (beyond repairing any cross-skill link touched).
- MCP tool behavior changes.
- Authoring-side guidance (belongs to `authoring-gradle-builds`).
- Exhaustive enumeration of every CLI option (explicitly not required).
- Archiving the OpenSpec change.

### User Stories
- As an agent running builds, I want the skill to tell me which CLI options actually matter for a feature engineer (task selection, `--tests`, `--rerun`, `--continue`, `--offline`, `--scan`, `--warning-mode`, `--configuration-cache`, info/debug logging, `--dry-run`, `--project-dir`/`-p`, `--gradle-user-home`/`-g`, `--no-daemon`, `--stop`) and which to avoid, so I drive the build correctly.
- As an agent, I want a clear "always use the daemon" default with the lifecycle facts (start/stop/reuse, `--stop`, idle timeout, when a daemon is forked, `GRADLE_USER_HOME` daemon state) and the rare cases to disable it, so builds stay fast and I don't kill the daemon by reflex.
- As an agent dropped into an unfamiliar repo, I want the standard Gradle directory layout (wrapper, `gradle/`, `buildSrc`, `gradle.properties`, settings/build scripts, `build/` outputs) so I can orient quickly.
- As an agent tuning a build's environment, I want authoritative build-environment guidance — `gradle.properties` (project vs user vs system), environment variables, JVM arguments for the build/daemon JVM (`org.gradle.jvmargs`), proxy settings, `GRADLE_USER_HOME`, and the precedence order — so I change the right knob in the right place.

### Functional Requirements
1. **Coverage**: every prioritized topic from the four pages is present in `using-gradle`, each in one home, framed for operating an existing build.
2. **Opinion + version-awareness**: each added topic states the recommended default and the anti-pattern; inline `Version notes` + 7.x fallbacks where behavior differs across Gradle 7/8/9.
3. **Authoritative routing**: added topics carry verified `gradle_docs` hints + published `docs.gradle.org` URLs and, where a project MCP tool applies, a pointer to its published page under `https://gradle-mcp.rnett.dev/latest/tools/`. No fabricated pointers.
4. **Build environment depth**: the build-environment material (the user's priority) is covered with real depth — property sources + precedence, `org.gradle.jvmargs`, environment variables, proxy, `GRADLE_USER_HOME` — not a token mention.
5. **Daemon opinion**: the skill states "always use the daemon" as the default, with the lifecycle facts and the narrow exceptions.
6. **Portfolio conventions preserved**: provenance headers (`class: authored-local`), lean `SKILL.md`, no dead links/orphans, `_index.md` regenerated only via `materializeSkills`.

### Non-Functional Requirements
- Audience is AI agents: dense, directive, machine-consumable; no narrative padding.
- `./gradlew check` gates pass: `verifySkillsMaterialized`, `verifySkillsList`; `SkillMaterializationTest` passes; `openspec validate redesign-gradle-skills-portfolio --strict` passes.
- No dead links or orphaned references; generated corpus (if any touched) unchanged.

# Technical Design

### Current Implementation
- `using-gradle` skill: `SKILL.md` + 6 references (`build-orientation.md`, `running-builds.md`, `testing.md`, `troubleshooting.md`, `dependencies.md`, `research.md`) + generated `references/_index.md`. The `using-gradle` manifest block is ~21 `IndexRow`s in `SkillMaterialization.kt` (lines ~49–70).
- Prior coverage briefs are the format template: `reports/using-gradle-basics-coverage-brief.md`, `reports/using-gradle-gotchas-brief.md`, `reports/using-gradle-docs-references-brief.md`, `reports/using-gradle-content-brief.md`.
- CLI command grammar is already mapped through the MCP `gradle` tool in `running-builds.md`; daemon/properties controls already appear in `troubleshooting.md`; filesystem markers already appear in `build-orientation.md`. The Stage 1 brief determines what is genuinely missing vs. already covered.
- Verification (`SkillMaterialization.verify`): inventory, provenance headers, generated hashes + index freshness, dead-link check, orphan check. No line-count assertion.

### Key Decisions
1. **Operating angle only**: content is for running/inspecting/diagnosing an existing build; authoring detail hands off to `authoring-gradle-builds`.
2. **Selective CLI coverage**: cover the options a feature engineer reaches for; explicitly not every option.
3. **Homes fixed by the brief**: enrich existing references in place; add a new reference only if the brief identifies a coherent new single home (build-environment is the likely candidate given its priority and volume). Nothing is trimmed for length.
4. **House style carried over**: inline `Version notes`, opinionated defaults/anti-patterns, verified doc/MCP pointers, orchestration via named workers.

### Proposed Changes
- Stage 1 brief maps each of the four pages' topics to current `using-gradle` content, marks gap/partial/covered, recommends a home per gap, and records verified `gradle_docs` hints + published URLs + relevant MCP-tool pointers.
- Integration per the brief: enrich `running-builds.md` (CLI options), `troubleshooting.md` and/or a new build-environment reference (build environment + daemon), `build-orientation.md` (directory layout); add a new reference + manifest row only if the brief justifies it.
- Sync manifest (`materializeSkills`), docs (`updateSkillsList` if descriptions change), and the OpenSpec delta if warranted; run gates.
- Independent doc-review pass; fix material findings; re-verify.

### Risks
- **Duplication**: CLI/daemon/directory content already partially exists; the brief must classify gap vs. covered to avoid restating. Mitigation: explicit gap map + single-home rule.
- **Over-enumeration of CLI options**: Mitigation: selective, operating-angle filter in the brief.
- **Fabricated pointers**: Mitigation: verify every `gradle_docs` path and URL in Stage 1; never invent.
- **Scope drift into authoring**: Mitigation: operating-angle framing + handoff rule.

# Testing

### Validation Approach
Fact-checked research before writing; deterministic build gates after; document-quality review at the end. Main session orchestrates only: research to `technical-researcher`; prose to `writer` (fallback `mid-delegate`); manifest/spec/gates to `coder` (with `build-expert`); audit to `doc-reviewer` with fixes via `coder`.

### Key Scenarios
- Each prioritized topic from the four pages is present in exactly one home, framed for operating a build.
- Build-environment coverage has real depth (property sources + precedence, `org.gradle.jvmargs`, env vars, proxy, `GRADLE_USER_HOME`).
- Daemon guidance states "always use the daemon" with lifecycle facts + narrow exceptions.
- CLI coverage is selective and opinionated, not exhaustive.

### Edge Cases
- Gradle 7.x: differing advice carries an inline fallback.
- Structural verifier: no dead links/orphans; `_index.md` matches manifest; provenance headers present.
- Pointers: every `gradle_docs` hint resolves and every URL is fetchable.

### Test Changes
- No new automated tests — `SkillMaterializationTest` asserts the structural contract.
- Mandatory commands: `./gradlew materializeSkills`, `./gradlew updateSkillsList`, `./gradlew :test --tests "dev.rnett.gradle.mcp.skills.SkillMaterializationTest"`, `./gradlew check`; plus `openspec validate redesign-gradle-skills-portfolio --strict` if the delta changes.

# Delivery Steps

### ✓ Step 1: Research the four pages and produce a coverage brief
Read the four live Gradle user-guide pages, map their topics against the current `using-gradle` skill, classify each as gap/partial/covered, recommend a home per gap, and record verified doc/MCP pointers. Delegate to `technical-researcher`.
- Read `command_line_interface.html` (selective, operating angle), `gradle_daemon.html` (always-use-daemon opinion + lifecycle), `directory_layout.html`, and `build_environment.html` (depth: property sources + precedence, `org.gradle.jvmargs`, env vars, proxy, `GRADLE_USER_HOME`).
- Map against the six current references; mark gap/partial/covered; recommend a single home per gap; flag whether a new build-environment reference is warranted.
- Verify every `gradle_docs` hint and published URL; record relevant MCP-tool pointers; never fabricate.
- Output `reports/using-gradle-cli-daemon-env-brief.md`.

### ✓ Step 2: Integrate the coverage into the skill
Enrich the affected references (and add a new reference + manifest row only if the brief justifies it), in house style. Delegate to `writer` (fallback `mid-delegate`).
- Add the gap topics with opinionated defaults/anti-patterns, `Version notes` + 7.x fallbacks, and verified "More info" pointers; preserve provenance headers; keep `SKILL.md` lean.

### ✓ Step 3: Sync manifest/spec and run gates
Regenerate `_index.md` if routing changed, reconcile the OpenSpec delta if warranted, and run all gates. Delegate to `coder` (with `build-expert`).
- `materializeSkills`, `updateSkillsList`, `SkillMaterializationTest`, `check`; `openspec validate ... --strict` if the delta changed.

### ✓ Step 4: Review and converge
Independent document-quality audit; fix material findings; re-verify. Delegate review to `doc-reviewer`, fixes to `coder`.
- Audit coverage of the four pages, operating-angle framing, opinionation, version-awareness, single-home dedup, pointer validity; fix material findings; re-run gates clean.
