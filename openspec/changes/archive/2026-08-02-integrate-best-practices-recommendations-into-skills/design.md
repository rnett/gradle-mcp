## Context

The skill materialization machinery requires every skill markdown file to carry a provenance header with class `authored-local`, `authored-shared`, or `generated`. Generated files carry body hashes checked by `checkGeneratedContent`; references must be reachable from `SKILL.md`; blocked documentation URLs are rejected by `checkNoBlockedDocUrls`; and shared sources fan out from `src/main/skill-sources/authored-shared`.

`generateBestPracticesDoc` writes `references/best-practices/` and runs as a dependency of verification and materialization. That directory is therefore effectively immutable to hand edits. New authored guidance must live outside it. The recommendation document's audience tags map directly to `using-gradle` and `authoring-gradle-builds`; its `Why (incl. why hard to figure out)` field supplies the criterion for selecting body rules. Its references are already `gradle_docs` paths and do not require blocked URLs.

## Goals / Non-Goals

**Goals:**

- Place all 134 recommendations in either body rules or authored references according to the agreed split.
- Preserve the frozen generated corpus byte-for-byte.
- Keep skill bodies within budget through progressive disclosure.
- Make every entry traceable by title and audience.
- Flag version-sensitive guidance and require wrapper-version inspection.

**Non-Goals:**

- No per-entry restatement in OpenSpec requirements.
- No tool, generator, verifier, or frontmatter/routing changes.
- No change to the generated corpus or its generated lookup.
- No new generated root index.

## Decisions

### D1. Single umbrella change

All three genuinely changing capabilities are covered by one change so the cross-cutting placement, corpus, and traceability invariants land atomically. Runtime skill additions remain content-only and receive no delta specs.

### D2. Preserve the generated corpus

The generated `references/best-practices/` directory remains untouched. Additive authored content uses class `authored-local` and is placed outside that directory. Corpus hashes and regeneration continue to enforce immutability.

### D3. Body versus reference placement

A recommendation is a body rule if it is one of the 13 hardest-to-figure-out highlights or is High severity and cross-cutting. Every other recommendation belongs in references. Every do/don't snippet belongs in references, including snippets associated with body rules. Body rules contain a compact rule, one-line reason, and reference link.

### D4. Progressive disclosure

Use the body for the always-loaded rule and its short rationale. Put full rationale, snippets, and `gradle_docs` pointers in an existing authored reference where a natural home exists; otherwise create a new authored-local reference linked from the skill body. Authored guidance is the procedural load. The frozen corpus remains optional rationale through `Index -> Detail -> Gradle Docs` and is never restated.

### D5. Version-sensitive guidance

Propagate `(version-sensitive)` markers from the recommendation source. Before applying such guidance, read `gradle/wrapper/gradle-wrapper.properties` and use the exact wrapper version. Preserve the caveat that the research used 9.6.1 documentation while the project targets 9.4.1.

### D6. Traceability source and key

`reports/gradle-best-practices-recommendations.md` is the source of truth and is not shipped. Coverage is keyed by entry title plus audience tag and severity. Each of the 134 entries maps to exactly one body rule or reference location.

### D7. Capability scope and exclusions

The two hub skills own the recommendation split because their audience tags map one-to-one to them. Runtime JVM, toolchain, and daemon fits are content-only additions to the existing shared setup materialization and local troubleshooting reference. The excluded capabilities are compliance boundaries or machinery and receive no normative change.

### D8. Runtime materialization

If runtime fits are needed, edit the single authored-shared source `src/main/skill-sources/authored-shared/repl-session-setup.md`, then run `materializeSkills`. Never edit materialized copies directly; drift checks must remain clean.

## Risks / Trade-offs

- Body bloat is mitigated by limiting body content to highlights and High cross-cutting rules, with one-line reasons and links.
- Duplication with the frozen corpus is mitigated by cross-linking and the modified `Best Practices Integration` contract, never restating corpus detail.
- Version sensitivity is mitigated by per-entry flags and the mandatory wrapper-version read.
- OpenSpec validation is not part of the Gradle check gate, so tasks include an explicit strict validation step.
- Traceability decay is mitigated by a coverage checklist keyed to every source entry title and verified in the final phase.

## Migration Plan

1. Add hub-skill body rules and, optionally, minimal runtime fits.
2. Weave reference content into existing authored references and create new authored-local references only where needed.
3. Reconcile against the frozen corpus, confirm byte identity, and replace any duplicated generated prose with links.
4. Author and validate the three delta specs.
5. Run the traceability audit and the implementation gate.

Each phase is independently revertible. The generated corpus is never modified.

## Open Questions

- The exact 13 highlights are resolved during Phase 1 by ranking the recommendation document's `Why (incl. why hard to figure out)` fields; the selected list is recorded in change notes.
- Whether any recommendation lacks a natural authored-reference home is resolved during Phase 2 mapping; such entries require a new authored-local reference.
