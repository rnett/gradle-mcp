---
name: advanced-gradle-dependencies
description: |
  Diagnoses and fixes advanced Gradle dependency resolution problems across the operate/author split: variant-aware resolution diagnostics, dependency verification, component metadata rules, substitution and composite builds, and dependency governance.

  ## Positive Triggers (when to activate)
  - Variant selection failures or attribute mismatches, diagnosed via `outgoingVariants` and `dependencyInsight --all-variants`
  - Dependency verification metadata, PGP keys, and CI verification workflows
  - Component metadata rules, selection rules, dependency substitution, and composite build diagnosis
  - Capability conflicts, feature variants, lock modes, advanced version catalogs beyond everyday catalog entries, repository governance, and caching/freshness tuning

  ## Negative Triggers (when NOT to activate)
  - Everyday dependency inspection, conflicts, or updates -> `using-gradle`
  - Dependency declarations, basic version catalogs, or basic locking -> `authoring-gradle-builds`
  - Running builds or generic failure diagnosis -> `using-gradle`
  - Non-dependency structural authoring -> `authoring-gradle-builds`
license: Apache-2.0
metadata:
  author: https://github.com/rnett/gradle-mcp
  version: "1.1.0"
---

# Advanced Gradle Dependency Engineering

Diagnoses and fixes advanced Gradle dependency resolution problems as diagnose-to-fix loops across the operate/author split. This skill owns the cross-cutting lane between everyday dependency inspection (`using-gradle`) and basic dependency authoring (`authoring-gradle-builds`).

**More info**: Search the User Guide with `gradle_docs(query="tag:userguide <term>")`. Read `gradle/wrapper/gradle-wrapper.properties` before any version-sensitive advice; this skill's wrapper-first scoping is mandatory because resolution behavior is version-sensitive.

## Constitution

- **ALWAYS** use the Gradle MCP `gradle` tool (or the dedicated dependency inspection tools) instead of `./gradlew` via shell for all resolution work.
- **ALWAYS** read the wrapper version (`gradle/wrapper/gradle-wrapper.properties`) before applying version-sensitive advice; resolution reports, variant model behavior, and governance modes change across Gradle versions.
- **ALWAYS** diagnose before fixing: run the matching authoritative report first (`dependencyInsight`, `outgoingVariants`, graph inspection), then apply the minimal authoring fix, then re-run the diagnostic to confirm resolution.
- **ALWAYS** use `query_build(kind="TESTS")` for tests and `query_build` for diagnostics; avoid raw console parsing.
- **NEVER** prescribe a resolution fix without a diagnostic step; variant mismatches, capability conflicts, and substitution behavior must be evidenced before authoring a rule.
- **NEVER** fabricate tool names; cite authoritative documentation only through `gradle_docs` hints (`path=...` or `query="tag:...")`.
- **Handoff**: everyday dependency inspection belongs to `using-gradle`; basic dependency declaration, version-catalog basics, and basic locking belong to `authoring-gradle-builds`; see [Cross-Skill Handoffs](#cross-skill-handoffs).

## Decision Routing

| Advanced dependency task | Reference |
|---|---|
| Diagnose variant selection failures, attribute mismatches, or no-matching-variant errors | [Variant Resolution Diagnostics](references/variant-resolution-diagnostics.md) |
| Enable or repair dependency verification (`verification-metadata.xml`, PGP keys, checksums, CI) | [Dependency Verification](references/dependency-verification.md) |
| Author or troubleshoot component metadata rules and dependency selection rules | [Component Metadata Rules](references/component-metadata-rules.md) |
| Diagnose dependency substitution or composite-build resolution (composite authoring lives in `authoring-gradle-builds`) | [Substitution and Composites](references/substitution-and-composites.md) |
| Resolve feature-variant selection, configuration-role, or capability-conflict problems | [Feature Variants and Capabilities](references/feature-variants-and-capabilities.md) |
| Apply lock modes or deep locking behavior beyond the basics | [Dependency Locking Deep Dive](references/dependency-locking-deep-dive.md) |
| Author advanced version catalog topics (bundles, plugins, multiple catalogs, composition) | [Advanced Version Catalogs](references/advanced-version-catalogs.md) |
| Govern repository declaration modes, content filtering, or `exclusiveContent` | [Repository Governance](references/repository-governance.md) |
| Reason about dependency cache freshness, resolution consistency, or resolution avoidance/performance | [Resolution Mechanics](references/resolution-mechanics.md) |

## Reference Discovery

Read the linked reference as part of the workflow: use [Variant Resolution Diagnostics](references/variant-resolution-diagnostics.md) when a variant selection fails or attributes do not match; use [Dependency Verification](references/dependency-verification.md) when explicitly asked to enable, repair, or CI-integrate `verification-metadata.xml`; use [Component Metadata Rules](references/component-metadata-rules.md) when a resolution outcome is wrong and a component metadata or selection rule is the correct lever; use [Substitution and Composites](references/substitution-and-composites.md) when a replacement, module dependency substitution, or composite build diagnosis is needed; use [Feature Variants and Capabilities](references/feature-variants-and-capabilities.md) for feature-variant selection, configuration-role, and capability-conflict resolution; use [Dependency Locking Deep Dive](references/dependency-locking-deep-dive.md) for lock modes and deep locking behavior; use [Advanced Version Catalogs](references/advanced-version-catalogs.md) for catalog topics beyond everyday entries; use [Repository Governance](references/repository-governance.md) for `dependencyResolutionManagement` modes, content filtering, and `exclusiveContent`; and use [Resolution Mechanics](references/resolution-mechanics.md) when reasoning about caching/freshness, resolution consistency, or resolution avoidance and performance.

## Cross-Skill Handoffs

- **Everyday Dependency Inspection** (graph audits, `dependencyInsight` winner analysis, force/exclude/platform/constraint menu, cache TTL vs `--refresh-dependencies`, update discovery, trivial dependency edits) $\rightarrow$ `using-gradle`.
- **Basic Dependency Authoring** (dependency declarations, version-catalog basics, repositories and content filters, constraints/BOMs, basic locking, custom-attribute/feature-variant basics) $\rightarrow$ `authoring-gradle-builds`.
- **Composite Build Authoring** (`includeBuild` declarations, plugin-management inclusion, buildSrc vs composite trade-offs, cross-build task wiring) $\rightarrow$ `authoring-gradle-builds`. This skill keeps composite-build diagnosis and dependency-substitution authoring.
- Receives advanced dependency engineering routed out of `using-gradle` and `authoring-gradle-builds`.

## Workflows

### Diagnose a Variant Selection Failure

1. Load [Variant Resolution Diagnostics](references/variant-resolution-diagnostics.md).
2. Read the wrapper version and record the failing configuration and requested attributes.
3. Diagnose with `dependencyInsight --all-variants` and the `outgoingVariants` report before authoring any rule.
4. Apply the minimal fix: an attribute compatibility or disambiguation rule on the correct side of the resolution.
5. Re-run the diagnostic to confirm the variant now matches.

### Enable or Repair Dependency Verification

1. Load [Dependency Verification](references/dependency-verification.md).
2. Confirm the request is an explicit supply-chain hardening request; report the UX costs first per the conditional-only doctrine.
3. Author or repair `verification-metadata.xml` (PGP keys, checksums, CI workflow) per the reference.
4. Re-run resolution to confirm verification is satisfied without disabling it.

### Correct a Wrong Resolution Outcome

1. Confirm the wrapper version and the resolved graph via `inspect_dependencies` / `dependencyInsight`.
2. Identify whether the cause is a component metadata rule, a selection rule, substitution, or a capability conflict by loading the matching reference.
3. Apply the smallest rule, then re-run the diagnostic to confirm the winner changed as intended.
