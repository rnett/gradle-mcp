## 1. Body rules and runtime fits

- [x] 1.1 Rank the recommendations document by `Why (incl. why hard to figure out)`, select the 13 highlights, and record the list in change notes.
- [x] 1.2 Add `[Writes build logic]` High cross-cutting and highlight rules to the `authoring-gradle-builds` `SKILL.md` body, with one-line reasons and reference links.
- [x] 1.3 Add `[Runs builds]` High cross-cutting and highlight rules to the `using-gradle` `SKILL.md` body.
- [x] 1.4 Optionally add minimal clear-fit JVM, toolchain, and daemon entries to `src/main/skill-sources/authored-shared/repl-session-setup.md` (not applicable; the shared REPL setup has no JVM/toolchain/daemon ownership).
- [x] 1.5 If 1.4 applies, run `materializeSkills` and verify the generated copies are synchronized (not applicable; `verifySkillsMaterialized` passed without shared-source changes).

## 2. Reference content

- [x] 2.1 Map every non-body `[Writes build logic]` entry to existing authored references such as `build-lifecycle.md`, `managed-types-and-providers.md`, `custom-tasks.md`, `dependencies-and-catalogs.md`, `convention-plugins.md`, `plugin-development.md`, `jdk-toolchains.md`, and `configurations-and-variants.md`, weaving guidance, do/don't snippets, and `gradle_docs` hints.
- [x] 2.2 Map every non-body `[Runs builds]` entry to `running-builds.md`, `troubleshooting.md`, `build-environment.md`, `dependencies.md`, `testing.md`, and `research.md`.
- [x] 2.3 Create new authored-local reference files only where no existing reference has a natural home; add the provenance header and a body link (no new shipped reference was needed).
- [x] 2.4 Propagate `(version-sensitive)` flags and add a wrapper-version check note before applying each flagged entry.

## 3. Frozen-corpus reconciliation

- [x] 3.1 Confirm that `references/best-practices/*.md` remains byte-identical across all 38 frozen files.
- [x] 3.2 Audit authored content for duplication and replace restated corpus prose with `Index -> Detail -> Gradle Docs` cross-links.

## 4. OpenSpec delta specs

- [x] 4.1 Author `specs/gradle-skill-best-practices-integration/spec.md` with four ADDED requirements and one MODIFIED requirement.
- [x] 4.2 Author `specs/authoring-gradle-builds/spec.md` with two ADDED requirements and one MODIFIED requirement.
- [x] 4.3 Author `specs/using-gradle/spec.md` with two ADDED requirements.

## 5. Validation and gate

- [x] 5.1 Run `openspec validate integrate-best-practices-recommendations-into-skills --strict`.
- [x] 5.2 Audit traceability so every one of the 134 entry titles maps to exactly one body rule or reference location, with no orphan entry.
- [x] 5.3 Run `./gradlew :check`, including `verifySkillsMaterialized`, `verifySkillsList`, and `verifyToolsList` (the verification tasks passed; full `check` retained two pre-existing integration failures).
